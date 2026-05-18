package com.chat.chat_event_sourcing.websocket.handler;

import com.chat.chat_event_sourcing.api.exception.ChatException;
import com.chat.chat_event_sourcing.domain.event.entity.Event;
import com.chat.chat_event_sourcing.domain.event.repository.EventRepository;
import com.chat.chat_event_sourcing.domain.session.entity.Participant;
import com.chat.chat_event_sourcing.domain.session.entity.Session;
import com.chat.chat_event_sourcing.domain.session.repository.ParticipantRepository;
import com.chat.chat_event_sourcing.domain.session.repository.SessionRepository;
import com.chat.chat_event_sourcing.domain.user.entity.User;
import com.chat.chat_event_sourcing.domain.user.repository.UserRepository;
import com.chat.chat_event_sourcing.websocket.dto.WebSocketAckMessage;
import com.chat.chat_event_sourcing.websocket.dto.WebSocketEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketHandler {

    private final SimpMessagingTemplate messagingTemplate;
    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;

    @MessageMapping("/sessions/{sessionId}/send")
    public void handleMessage(
            @DestinationVariable Long sessionId,
            @Payload WebSocketEventMessage message,
            @Header("X-User-Id") String userIdHeader) {

        Long userId = Long.parseLong(userIdHeader);

        try {
            Session session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("세션을 찾을 수 없습니다."));

            if (session.getStatus() != Session.Status.ACTIVE) {
                sendError(userId, "SESSION_ENDED", "종료된 세션입니다.");
                return;
            }

            if (!participantRepository.existsBySessionIdAndUserId(sessionId, userId)) {
                sendError(userId, "FORBIDDEN", "세션 참여자가 아닙니다.");
                return;
            }

            // 중복 이벤트 확인
            Optional<Event> existing = eventRepository
                    .findBySessionIdAndIdempotencyKey(sessionId, message.getIdempotencyKey());
            if (existing.isPresent()) {
                sendAck(userId, existing.get());
                return;
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

            // server_seq 발급
            long serverSeq = session.getLastEventSeq() + 1;
            session.updateLastEventSeq(serverSeq);
            sessionRepository.save(session);

            Event event = eventRepository.save(Event.builder()
                    .session(session)
                    .user(user)
                    .eventType(Event.Type.valueOf(message.getEventType()))
                    .clientSeq(message.getClientSeq())
                    .serverSeq(serverSeq)
                    .idempotencyKey(message.getIdempotencyKey())
                    .payload(message.getPayload())
                    .clientTs(message.getClientTs())
                    .build());

            // 브로드캐스트
            Map<String, Object> broadcastMsg = new HashMap<>();
            broadcastMsg.put("eventId", event.getId());
            broadcastMsg.put("eventType", event.getEventType().name());
            broadcastMsg.put("sessionId", sessionId);
            broadcastMsg.put("userId", userId);
            broadcastMsg.put("serverSeq", event.getServerSeq());
            broadcastMsg.put("payload", event.getPayload());
            broadcastMsg.put("serverTs", event.getServerTs().toString());

            messagingTemplate.convertAndSend(
                    "/topic/sessions/" + sessionId,
                    (Object) broadcastMsg
            );

            // ACK 발송
            sendAck(userId, event);

        } catch (ChatException e) {
            sendError(userId, e.getErrorCode().getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("WebSocket 메시지 처리 중 오류", e);
            sendError(userId, "INTERNAL_ERROR", "서버 내부 오류입니다.");
        }
    }

    @MessageMapping("/sessions/{sessionId}/join")
    public void handleJoin(
            @DestinationVariable Long sessionId,
            @Payload WebSocketEventMessage message,
            @Header("X-User-Id") String userIdHeader) {

        Long userId = Long.parseLong(userIdHeader);

        try {
            Session session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("세션을 찾을 수 없습니다."));

            Participant participant = participantRepository
                    .findBySessionIdAndUserId(sessionId, userId)
                    .orElseThrow(() -> new RuntimeException("참여자를 찾을 수 없습니다."));

            participant.reconnect();
            participantRepository.save(participant);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

            // JOIN 이벤트 저장
            long serverSeq = session.getLastEventSeq() + 1;
            session.updateLastEventSeq(serverSeq);
            sessionRepository.save(session);

            eventRepository.save(Event.builder()
                    .session(session)
                    .user(user)
                    .eventType(Event.Type.JOIN)
                    .clientSeq(message.getClientSeq())
                    .serverSeq(serverSeq)
                    .idempotencyKey(message.getIdempotencyKey())
                    .payload(Map.of("reason", "user_action"))
                    .clientTs(message.getClientTs())
                    .build());

            // 브로드캐스트
            Map<String, Object> joinMsg = new HashMap<>();
            joinMsg.put("eventType", "JOIN");
            joinMsg.put("sessionId", sessionId);
            joinMsg.put("userId", userId);
            joinMsg.put("serverSeq", serverSeq);
            joinMsg.put("serverTs", LocalDateTime.now().toString());

            messagingTemplate.convertAndSend(
                    "/topic/sessions/" + sessionId,
                    (Object) joinMsg
            );

        } catch (Exception e) {
            log.error("WebSocket JOIN 처리 중 오류", e);
            sendError(userId, "INTERNAL_ERROR", "서버 내부 오류입니다.");
        }
    }

    @MessageMapping("/sessions/{sessionId}/leave")
    public void handleLeave(
            @DestinationVariable Long sessionId,
            @Payload WebSocketEventMessage message,
            @Header("X-User-Id") String userIdHeader) {

        Long userId = Long.parseLong(userIdHeader);

        try {
            Session session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("세션을 찾을 수 없습니다."));

            Participant participant = participantRepository
                    .findBySessionIdAndUserId(sessionId, userId)
                    .orElseThrow(() -> new RuntimeException("참여자를 찾을 수 없습니다."));

            participant.leave();
            participantRepository.save(participant);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

            // LEAVE 이벤트 저장
            long serverSeq = session.getLastEventSeq() + 1;
            session.updateLastEventSeq(serverSeq);
            sessionRepository.save(session);

            eventRepository.save(Event.builder()
                    .session(session)
                    .user(user)
                    .eventType(Event.Type.LEAVE)
                    .clientSeq(message.getClientSeq())
                    .serverSeq(serverSeq)
                    .idempotencyKey(message.getIdempotencyKey())
                    .payload(Map.of("reason", "user_action"))
                    .clientTs(message.getClientTs())
                    .build());

            // 브로드캐스트
            Map<String, Object> leaveMsg = new HashMap<>();
            leaveMsg.put("eventType", "LEAVE");
            leaveMsg.put("sessionId", sessionId);
            leaveMsg.put("userId", userId);
            leaveMsg.put("serverSeq", serverSeq);
            leaveMsg.put("serverTs", LocalDateTime.now().toString());

            messagingTemplate.convertAndSend(
                    "/topic/sessions/" + sessionId,
                    (Object) leaveMsg
            );

        } catch (Exception e) {
            log.error("WebSocket LEAVE 처리 중 오류", e);
            sendError(userId, "INTERNAL_ERROR", "서버 내부 오류입니다.");
        }
    }

    private void sendAck(Long userId, Event event) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/ack",
                WebSocketAckMessage.builder()
                        .idempotencyKey(event.getIdempotencyKey())
                        .serverSeq(event.getServerSeq())
                        .serverTs(event.getServerTs())
                        .status("ACCEPTED")
                        .build()
        );
    }

    private void sendError(Long userId, String code, String message) {
        Map<String, Object> errorMsg = new HashMap<>();
        errorMsg.put("code", code);
        errorMsg.put("message", message);
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/errors",
                (Object) errorMsg
        );
    }
}