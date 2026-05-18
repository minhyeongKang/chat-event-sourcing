# 이벤트 기반 상태 복원 전략

## 1. 개요

본 시스템은 Event Sourcing 패턴을 기반으로 모든 이벤트를 불변(immutable)으로 저장하며,
이를 통해 임의 시점의 세션 상태를 재현할 수 있습니다.

---

## 2. 핵심 개념

### Event Store
- `events` 테이블이 유일한 진실의 원천(Single Source of Truth)입니다.
- 이벤트는 한 번 저장되면 수정/삭제되지 않습니다.
- 메시지 수정/삭제도 새로운 이벤트(`MESSAGE_EDITED`, `MESSAGE_DELETED`)로 표현합니다.

### server_seq
- 서버가 단조 증가(monotonically increasing) 방식으로 발급하는 공식 순서 번호입니다.
- 클라이언트 시각(`client_ts`)은 신뢰할 수 없으므로, `server_seq`가 이벤트 순서의 공식 기준입니다.
- `sessions.last_event_seq`를 기준으로 `+1`씩 증가하며, UNIQUE 제약으로 중복을 방지합니다.

---

## 3. 복원 전략

### 3.1 Full Replay (전체 리플레이)

스냅샷이 없는 경우, 세션의 모든 이벤트를 `server_seq` 순서로 리플레이하여 상태를 재현합니다.

```
seq=1 (JOIN)  → seq=2 (MESSAGE) → seq=3 (MESSAGE) → ... → seq=N
                                                              ↓
                                                        at 시점 상태
```

**단점**: 이벤트가 많아질수록 복원 비용이 선형으로 증가합니다.

---

### 3.2 Snapshot + Delta Replay (스냅샷 + 델타 리플레이) — 권장

복원 기준 시각(`at`) 이전의 가장 최근 스냅샷을 찾고,
스냅샷 이후의 델타 이벤트만 리플레이하여 복원 비용을 줄입니다.

```
[스냅샷 at seq=50]  +  [seq=51 ~ at 시점 이벤트 리플레이]
         ↓
    at 시점 상태
```

**장점**: 리플레이 대상 이벤트 수를 대폭 줄여 복원 성능을 향상시킵니다.

**스냅샷 생성 주기**: 매 50 이벤트마다 자동 생성을 권장합니다. (현재는 수동 생성 API 제공)

---

### 3.3 복원 흐름 (코드 기준)

```
GET /sessions/{id}/timeline?at=...
        ↓
1. 기준 시각(at)의 server_seq 조회
        ↓
2. at_server_seq <= 기준 seq 인 스냅샷 중 가장 최근 것 탐색
        ↓
3. 스냅샷 있음 → SNAPSHOT_PLUS_DELTA
   스냅샷 없음 → FULL_REPLAY (fromSeq = 0)
        ↓
4. 델타 이벤트 순서대로 리플레이 (applyEvent)
        ↓
5. 최종 상태 반환 (participants + messages)
```

---

## 4. 이벤트 타입별 상태 반영 규칙

| 이벤트 타입 | 상태 반영 |
|-------------|-----------|
| `JOIN` | 참여자 status → JOINED, leftAt → null |
| `LEAVE` | 참여자 status → LEFT, leftAt 기록 |
| `DISCONNECT` | 참여자 status → DISCONNECTED |
| `RECONNECT` | 참여자 status → JOINED, leftAt → null |
| `MESSAGE` | 메시지 추가, status → SENT |
| `MESSAGE_EDITED` | 해당 메시지 content 수정, status → EDITED |
| `MESSAGE_DELETED` | 해당 메시지 status → DELETED |

---

## 5. 중복 이벤트 처리

### 발생 원인
네트워크 불안정으로 클라이언트가 동일 이벤트를 재전송할 수 있습니다.

### 처리 전략: idempotency_key

클라이언트는 이벤트 전송 시 고유한 `idempotency_key`를 생성하여 함께 전송합니다.

```
idempotency_key 생성 예시: "user-{userId}-seq-{clientSeq}"
```

서버는 `(session_id, idempotency_key)` UNIQUE 제약으로 중복 저장을 DB 레벨에서 차단합니다.

```sql
UNIQUE KEY uq_events_idempotency (session_id, idempotency_key)
```

중복 이벤트가 수신되면 기존 이벤트의 정보를 그대로 반환합니다. (멱등 응답)

### 복원 로직에서의 중복 처리
`server_seq` 기준으로 정렬하여 리플레이하므로, 중복 이벤트는 저장 단계에서 이미 차단되어
복원 로직에서는 별도 처리가 불필요합니다.

---

## 6. 순서 뒤바뀜 처리

### 발생 원인
네트워크 경로 차이로 이벤트 도착 순서가 클라이언트 발생 순서와 다를 수 있습니다.

### 처리 전략

| 기준 | 역할 |
|------|------|
| `client_seq` | 클라이언트 발생 순서 (참고용, 신뢰 불가) |
| `client_ts` | 클라이언트 발생 시각 (참고용, 신뢰 불가) |
| `server_seq` | 서버 수신 순서 (공식 기준, 단조 증가) |
| `server_ts` | 서버 수신 시각 (복원 기준) |

**서버 수신 순서(`server_seq`)를 공식 기준**으로 사용합니다.
클라이언트 시각이나 순서는 참고용으로만 저장하며, 정렬/복원의 기준으로 사용하지 않습니다.

---

## 7. 복원 성능 최적화

### 인덱스
```sql
-- 상태 복원 핫패스: seq 범위 스캔
CREATE INDEX idx_events_session_seq_ts
    ON events (session_id, server_seq, server_ts);

-- 스냅샷 탐색
CREATE INDEX idx_snapshots_session_seq
    ON snapshots (session_id, at_server_seq DESC);
```

### 스냅샷 주기
- 매 50 이벤트마다 스냅샷 자동 생성 권장
- 최대 리플레이 이벤트 수를 50개 이내로 제한

### Projection
- `projections` 테이블에 현재 최신 상태를 캐싱
- 현재 상태 조회 시 이벤트 리플레이 없이 즉시 반환 가능
- 이벤트 발생 시 비동기로 업데이트 (과거 복원과 분리)
