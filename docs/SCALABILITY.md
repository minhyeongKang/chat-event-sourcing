# 확장성 및 장애 대응

## 1. 서버 수평 확장 전략

### 1.1 문제: WebSocket 세션의 서버 친화성

WebSocket은 연결이 유지되는 동안 특정 서버 인스턴스에 고정됩니다.
서버가 여러 대일 경우, 같은 세션의 두 참여자가 서로 다른 서버에 연결될 수 있습니다.

```
사용자 A → 서버 1
사용자 B → 서버 2
→ 서버 1이 사용자 B에게 메시지를 직접 전달할 수 없음
```

### 1.2 해결: Redis Pub/Sub

Redis를 메시지 브로커로 사용하여 서버 간 메시지를 중계합니다.

```
사용자 A → 서버 1 → Redis Channel(session:1) → 서버 2 → 사용자 B
```

**채널 네이밍**: `session:{sessionId}`

```
이벤트 발생 시:
1. 이벤트 DB 저장
2. Redis Channel에 Publish
3. 모든 서버가 해당 Channel Subscribe 중
4. 각 서버가 자신에 연결된 참여자에게 브로드캐스트
```

### 1.3 세션 분산 전략

| 항목 | 전략 |
|------|------|
| 로드밸런서 | Sticky Session (같은 세션은 같은 서버로) 또는 Redis Pub/Sub |
| 상태 저장 | Redis (Presence, 세션 캐시) |
| DB | MySQL 단일 Primary (읽기 분산 시 Read Replica 추가) |

---

## 2. 관측 가능성 (Observability)

### 2.1 로그 (Logging)

```
구조화 로그 형식 (JSON):
{
  "timestamp": "2026-05-18T14:27:22.883",
  "level": "INFO",
  "sessionId": 2,
  "userId": 1,
  "eventType": "MESSAGE",
  "serverSeq": 43,
  "message": "이벤트 저장 완료"
}
```

**로그 레벨 기준**:
- `INFO`: 정상 이벤트 처리, 세션 생성/종료
- `WARN`: 중복 이벤트 수신, 재연결 감지
- `ERROR`: 이벤트 저장 실패, DB 연결 오류

### 2.2 메트릭 (Metrics)

Spring Boot Actuator로 아래 메트릭을 수집합니다.

| 메트릭 | 설명 |
|--------|------|
| `sessions.active` | 현재 활성 세션 수 |
| `events.saved` | 저장된 이벤트 수 |
| `events.duplicate` | 중복 이벤트 수신 횟수 |
| `websocket.connections` | 현재 WebSocket 연결 수 |
| `timeline.restore.duration` | 상태 복원 소요 시간 |
| `timeline.replayed.events` | 복원 시 리플레이된 이벤트 수 |

엔드포인트: `GET /actuator/metrics`

### 2.3 추적 (Tracing)

분산 추적을 위해 `X-Request-Id` 헤더를 통해 요청을 추적합니다.

```
클라이언트 → X-Request-Id: req-uuid-001
서버 → 모든 로그에 requestId 포함
```

---

## 3. 비동기 처리 설계

### 3.1 Projection 비동기 업데이트

이벤트 저장 후 `projections` 테이블 업데이트는 비동기로 처리하여
이벤트 저장 응답 지연을 최소화합니다.

```
이벤트 저장 (동기) → ACK 반환
        ↓ (비동기)
Projection 업데이트 (Spring @Async 또는 Redis Queue)
```

### 3.2 Snapshot 자동 생성

매 50 이벤트마다 스냅샷을 자동 생성합니다. (비동기)

```
이벤트 저장 후:
if (session.last_event_seq % 50 == 0) {
    → 비동기로 Snapshot 생성 트리거
}
```

### 3.3 재시도 및 DLQ

| 항목 | 전략 |
|------|------|
| 재시도 | 지수 백오프 (1s → 2s → 4s → 8s, 최대 3회) |
| DLQ | 재시도 실패 시 Dead Letter Queue로 이동 |
| Idempotency | idempotency_key로 중복 실행 방지 |

---

## 4. 장애 대응 시나리오

### 4.1 서버 다운 (인스턴스 장애)

**감지**
- 로드밸런서 헬스체크 실패 (`/actuator/health`)
- WebSocket 연결 끊김 감지

**완화**
- 로드밸런서가 트래픽을 정상 인스턴스로 자동 라우팅
- 클라이언트 자동 재연결 (Exponential Backoff)

**복구**
```
1. 클라이언트 → WebSocket 재연결
2. 클라이언트 → POST /sessions/{id}/join
3. 서버 → last_event_seq 반환
4. 클라이언트 → GET /sessions/{id}/events?from_seq={last_event_seq}
5. 누락 이벤트 수신 및 상태 동기화
```

**데이터 손실 없음**: 이벤트는 MySQL에 영속 저장되므로 서버 재시작 후에도 복원 가능합니다.

---

### 4.2 DB 장애 또는 성능 저하

**감지**
- HikariCP 커넥션 풀 고갈 알림
- 쿼리 응답 시간 임계치 초과 (Slow Query Log)
- `SELECT 1` 헬스체크 실패

**커넥션 풀 고갈 방지 설정**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      connection-timeout: 3000
      idle-timeout: 600000
```

**완화**
- Read Replica로 읽기 트래픽 분산 (조회 API)
- Redis 캐시로 일시적 DB 부하 감소
- 이벤트 수집은 큐에 버퍼링 후 DB 복구 시 일괄 처리

**복구**
```
1. DB 복구 또는 Failover (Read Replica → Primary 승격)
2. 커넥션 풀 재초기화
3. 버퍼링된 이벤트 일괄 저장
4. Projection/Snapshot 재생성
```

---

### 4.3 데이터 유실 또는 정합성 이슈

**감지**
- `server_seq` 불연속 감지 (seq 1, 2, 4 → 3 누락)
- 중복 이벤트 카운트 급증
- 복원 결과와 실제 상태 불일치

**완화**
- `@Transactional`로 이벤트 저장과 `last_event_seq` 업데이트를 원자적으로 처리
- `idempotency_key` UNIQUE 제약으로 중복 저장 방지
- 이벤트 저장 실패 시 클라이언트에 에러 반환 (ACK 미전송 → 클라이언트 재전송)

**복구**
```
1. 누락 이벤트 탐지: server_seq 불연속 구간 조회
   SELECT server_seq FROM events
   WHERE session_id = ?
   ORDER BY server_seq ASC;

2. 클라이언트 로그와 대조하여 누락 이벤트 재수집

3. Projection/Snapshot 재생성
   → 전체 이벤트 리플레이로 상태 재구성
```

**부분 실패 처리**
이벤트 저장은 성공했지만 Projection 업데이트가 실패한 경우:
- Projection을 재생성하여 복구 (이벤트 리플레이 기반)
- 이벤트 자체는 불변이므로 언제든 재생성 가능
