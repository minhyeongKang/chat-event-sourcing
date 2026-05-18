# DB 설계 및 인덱스 전략

## 1. 설계 원칙

| 원칙 | 내용 |
|------|------|
| Event Store | `events` 테이블이 유일한 진실의 원천 |
| 불변성 | 이벤트는 INSERT만 허용, UPDATE/DELETE 금지 |
| 멱등성 | `idempotency_key` UNIQUE 제약으로 중복 저장 방지 |
| 순서 보장 | `server_seq`가 공식 순서 기준 |

---

## 2. 테이블 설계 선택 근거

### events.payload → JSON 타입 선택

**이유**: 이벤트 타입마다 payload 구조가 다릅니다.
- `MESSAGE`: `{content, messageId}`
- `MESSAGE_EDITED`: `{messageId, newContent}`
- `JOIN/LEAVE`: `{reason}`

각 타입마다 별도 컬럼을 만들면 NULL 컬럼이 대부분을 차지하는 비효율이 발생합니다.
JSON으로 저장하면 구조 변경 없이 새로운 이벤트 타입 추가가 가능합니다.

**트레이드오프**: JSON 내부 필드에 인덱스를 걸 수 없으므로, payload 기반 검색은 풀스캔이 필요합니다.
본 시스템에서 payload 기반 검색은 없으므로 허용 가능한 트레이드오프입니다.

### snapshots.state / projections.current_state → JSON 타입 선택

**이유**: 세션 상태는 참여자 목록과 메시지 목록을 포함하는 복합 구조입니다.
별도 테이블로 정규화하면 복원 시 JOIN이 늘어나 성능이 저하됩니다.
JSON으로 비정규화하여 단일 조회로 전체 상태를 가져올 수 있습니다.

### snapshots vs projections 분리

| | snapshots | projections |
|---|---|---|
| 목적 | 과거 시점 복원 | 현재 상태 빠른 조회 |
| 개수 | 세션당 N개 (히스토리) | 세션당 1개 |
| 업데이트 | 주기적 생성 | 이벤트마다 갱신 |

두 용도의 접근 패턴이 다르므로 분리하여 설계했습니다.

---

## 3. 주요 쿼리 및 인덱스

### 핫패스 1: 상태 복원 (`GET /sessions/{id}/timeline?at=`)

```sql
-- ① 기준 시각의 server_seq 조회
SELECT server_seq FROM events
WHERE session_id = ?
  AND server_ts <= ?
ORDER BY server_seq DESC
LIMIT 1;

-- ② 기준 seq 이전의 가장 최근 스냅샷 조회
SELECT * FROM snapshots
WHERE session_id = ?
  AND at_server_seq <= ?
ORDER BY at_server_seq DESC
LIMIT 1;

-- ③ 델타 이벤트 조회
SELECT * FROM events
WHERE session_id = ?
  AND server_seq > ?
  AND server_ts <= ?
ORDER BY server_seq ASC;
```

**인덱스**:
```sql
CREATE INDEX idx_events_session_seq_ts
    ON events (session_id, server_seq, server_ts);

CREATE INDEX idx_snapshots_session_seq
    ON snapshots (session_id, at_server_seq DESC);
```

**병목 및 개선 방향**:
- 이벤트가 대량 누적 시 `server_ts` 범위 스캔 비용 증가
- 스냅샷 주기를 줄여(매 50 → 매 20 이벤트) 델타 이벤트 수 감소
- Partition by `session_id`로 테이블 분할 고려 (대규모 운영 시)

---

### 핫패스 2: 이벤트 저장 및 중복 방지

```sql
-- 중복 확인
SELECT id, server_seq FROM events
WHERE session_id = ?
  AND idempotency_key = ?;

-- 이벤트 저장
INSERT INTO events
    (session_id, user_id, event_type, client_seq, server_seq,
     idempotency_key, payload, client_ts, server_ts)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(3));
```

**인덱스** (DDL에 포함):
```sql
UNIQUE KEY uq_events_idempotency (session_id, idempotency_key)
UNIQUE KEY uq_events_server_seq (session_id, server_seq)
```

**병목 및 개선 방향**:
- `idempotency_key` 조회는 UNIQUE 인덱스로 O(log n) 보장
- 동시 이벤트 저장 시 `last_event_seq` 업데이트 락 경합 가능
- 개선: Redis INCR로 server_seq 발급 분리 (DB 락 제거)

---

### 핫패스 3: 세션 목록 조회 (`GET /sessions`)

```sql
SELECT DISTINCT s.*
FROM sessions s
JOIN participants p ON p.session_id = s.id
WHERE p.user_id = ?
  AND s.status = ?
  AND s.started_at >= ?
  AND s.started_at < ?
ORDER BY s.started_at DESC
LIMIT 20;
```

**인덱스**:
```sql
CREATE INDEX idx_sessions_status_started
    ON sessions (status, started_at DESC);

CREATE INDEX idx_participants_user_session
    ON participants (user_id, session_id);
```

**병목 및 개선 방향**:
- `DISTINCT` 사용으로 정렬 비용 발생
- 개선: 서브쿼리로 먼저 session_id 추린 후 JOIN
- 페이지네이션 필수 (기본 20개, 최대 100개)

---

## 4. 대량 데이터 처리 전략

### 파티셔닝 (대규모 운영 시)

```sql
-- session_id 기준 파티셔닝
ALTER TABLE events
PARTITION BY HASH(session_id)
PARTITIONS 16;
```

`session_id` 기준으로 파티셔닝하면 특정 세션의 이벤트 조회 시
해당 파티션만 스캔하여 성능을 향상시킬 수 있습니다.

### 아카이빙

종료된 세션(`ENDED`, `ABORTED`)의 이벤트는 별도 아카이브 테이블로 이동하여
핫 테이블의 크기를 줄입니다.

```sql
-- 30일 이상 지난 종료 세션 아카이빙 (배치)
INSERT INTO events_archive SELECT * FROM events
WHERE session_id IN (
    SELECT id FROM sessions
    WHERE status != 'ACTIVE'
    AND ended_at < NOW() - INTERVAL 30 DAY
);
```
