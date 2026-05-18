# 재연결 및 중복 처리 전략

## 1. 재연결 시나리오

### 1.1 정상 재연결 흐름

```
클라이언트 → WebSocket 연결 끊김 (네트워크 불안정)
        ↓
클라이언트 → POST /sessions/{id}/join 호출
        ↓
서버 → Participant.status = JOINED 업데이트
서버 → JOIN 이벤트 저장 (server_seq 발급)
서버 → last_event_seq 반환
        ↓
클라이언트 → GET /sessions/{id}/events?from_seq={last_event_seq}
        ↓
누락된 이벤트 수신 및 로컬 상태 동기화
        ↓
클라이언트 → WebSocket 재연결 완료
```

### 1.2 재연결 시 데이터 정합성 유지

재연결 후 클라이언트는 `last_event_seq`를 기준으로 누락된 이벤트를 요청합니다.

```
GET /sessions/{id}/events?from_seq=42
```

서버는 `server_seq > 42`인 이벤트를 순서대로 반환하며,
클라이언트는 이를 로컬에 적용하여 상태를 동기화합니다.

**핵심**: `server_seq`가 단조 증가하므로, 클라이언트는 자신이 마지막으로 받은 seq 이후의
이벤트만 요청하면 정확한 상태 복원이 가능합니다.

---

## 2. 중복 이벤트 처리

### 2.1 발생 원인

- 클라이언트가 이벤트를 전송했지만 ACK를 받지 못한 경우 재전송
- 네트워크 타임아웃으로 인한 자동 재시도
- WebSocket 재연결 후 미확인 이벤트 재전송

### 2.2 idempotency_key 전략

클라이언트는 이벤트 전송 시 고유한 키를 생성합니다.

```
idempotency_key = "user-{userId}-seq-{clientSeq}-{yyyyMMddHH}"
예시: "user-1-seq-15-2026051814"
```

서버 처리 흐름:

```
이벤트 수신
    ↓
idempotency_key로 중복 여부 확인
    ↓
중복 O → 기존 이벤트 정보 반환 (멱등 응답, 새로 저장 X)
중복 X → 이벤트 저장 후 ACK 반환
```

DB 레벨 보장:
```sql
UNIQUE KEY uq_events_idempotency (session_id, idempotency_key)
```

### 2.3 ACK 메커니즘

WebSocket 이벤트 처리 후 발신자에게 ACK를 전송합니다.

```json
// /user/queue/ack
{
  "idempotencyKey": "user-1-seq-15-2026051814",
  "serverSeq": 43,
  "serverTs": "2026-05-18T14:27:22.883",
  "status": "ACCEPTED"
}
```

클라이언트는 ACK 수신 전까지 이벤트를 재전송 대기 목록에 보관합니다.

---

## 3. 순서 뒤바뀜 처리

### 3.1 서버 수신 순서 기준

모든 이벤트는 서버 수신 순간 `server_seq`를 발급받습니다.
클라이언트 발생 순서(`client_seq`)나 시각(`client_ts`)은 참고용으로만 저장됩니다.

```
클라이언트 A: seq=1 전송 → 서버 도착: server_seq=2
클라이언트 B: seq=1 전송 → 서버 도착: server_seq=1
→ 공식 순서: B(server_seq=1) → A(server_seq=2)
```

### 3.2 순서 일관성 보장

- `sessions.last_event_seq`에서 `+1`로 server_seq를 발급
- `@Transactional`로 동시성 처리
- `UNIQUE KEY uq_events_server_seq (session_id, server_seq)`로 중복 발급 방지

---

## 4. Presence 관리

### 4.1 온라인/오프라인 상태

| 상태 | 설명 |
|------|------|
| `JOINED` | 세션에 참여 중 (온라인) |
| `LEFT` | 정상 퇴장 |
| `DISCONNECTED` | 비정상 연결 끊김 (오프라인) |

### 4.2 Redis를 활용한 실시간 Presence

```
WebSocket 연결 시 → Redis SET presence:{sessionId}:{userId} = "ONLINE" (TTL: 30s)
하트비트 수신 시 → TTL 갱신
WebSocket 끊김 시 → TTL 만료로 자동 오프라인 처리
```

### 4.3 DISCONNECT 이벤트 처리

WebSocket 연결이 끊기면 서버는 `DISCONNECT` 이벤트를 자동으로 저장하고
`participants.status`를 `DISCONNECTED`로 업데이트합니다.

재연결 성공 시 `RECONNECT` 이벤트를 저장하고 `JOINED`로 복원합니다.
