package com.chat.chat_event_sourcing.service;

import com.chat.chat_event_sourcing.api.dto.TimelineResponse;
import com.chat.chat_event_sourcing.api.exception.ChatException;
import com.chat.chat_event_sourcing.api.exception.ErrorCode;
import com.chat.chat_event_sourcing.domain.event.entity.Event;
import com.chat.chat_event_sourcing.domain.event.repository.EventRepository;
import com.chat.chat_event_sourcing.domain.session.entity.Session;
import com.chat.chat_event_sourcing.domain.session.repository.SessionRepository;
import com.chat.chat_event_sourcing.domain.snapshot.entity.Snapshot;
import com.chat.chat_event_sourcing.domain.snapshot.repository.SnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TimelineService {

    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final SnapshotRepository snapshotRepository;

    @Transactional(readOnly = true)
    public TimelineResponse restore(Long sessionId, LocalDateTime at) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ChatException(ErrorCode.SESSION_NOT_FOUND));

        if (at.isBefore(session.getStartedAt())) {
            throw new ChatException(ErrorCode.INVALID_REQUEST, "복원 기준 시각이 세션 시작 시각보다 이전입니다.");
        }

        // 기준 시각의 server_seq 조회
        List<Long> seqs = eventRepository.findServerSeqAtTime(sessionId, at);
        long atSeq = seqs.isEmpty() ? 0L : seqs.get(0);

        // 가장 가까운 스냅샷 탐색
        Optional<Snapshot> snapshotOpt = snapshotRepository.findClosestBefore(sessionId, atSeq);

        long fromSeq;
        String strategy;
        Long snapshotSeq = null;

        // 참여자/메시지 상태 초기화
        Map<Long, TimelineResponse.ParticipantState> participantMap = new LinkedHashMap<>();
        Map<Long, TimelineResponse.MessageState> messageMap = new LinkedHashMap<>();

        if (snapshotOpt.isPresent()) {
            // 스냅샷 + 델타 리플레이
            Snapshot snapshot = snapshotOpt.get();
            fromSeq = snapshot.getAtServerSeq();
            snapshotSeq = fromSeq;
            strategy = "SNAPSHOT_PLUS_DELTA";
            restoreFromSnapshot(snapshot, participantMap, messageMap);
        } else {
            // 전체 리플레이
            fromSeq = 0L;
            strategy = "FULL_REPLAY";
        }

        // 델타 이벤트 리플레이
        List<Event> deltaEvents = eventRepository.findDeltaEvents(sessionId, fromSeq, at);
        for (Event event : deltaEvents) {
            applyEvent(event, participantMap, messageMap);
        }

        return TimelineResponse.builder()
                .sessionId(sessionId)
                .restoredAt(at)
                .replayStrategy(strategy)
                .snapshotSeq(snapshotSeq)
                .replayedEventCount(deltaEvents.size())
                .state(TimelineResponse.State.builder()
                        .sessionStatus(session.getStatus().name())
                        .participants(new ArrayList<>(participantMap.values()))
                        .messages(new ArrayList<>(messageMap.values()))
                        .build())
                .build();
    }

    // 스냅샷에서 상태 복원
    @SuppressWarnings("unchecked")
    private void restoreFromSnapshot(Snapshot snapshot,
                                     Map<Long, TimelineResponse.ParticipantState> participantMap,
                                     Map<Long, TimelineResponse.MessageState> messageMap) {
        Map<String, Object> state = snapshot.getState();

        List<Map<String, Object>> participants =
                (List<Map<String, Object>>) state.getOrDefault("participants", List.of());
        for (Map<String, Object> p : participants) {
            Long userId = toLong(p.get("user_id"));
            participantMap.put(userId, TimelineResponse.ParticipantState.builder()
                    .userId(userId)
                    .username((String) p.get("username"))
                    .status((String) p.get("status"))
                    .joinedAt(parseDateTime(p.get("joined_at")))
                    .leftAt(parseDateTime(p.get("left_at")))
                    .build());
        }

        List<Map<String, Object>> messages =
                (List<Map<String, Object>>) state.getOrDefault("messages", List.of());
        for (Map<String, Object> m : messages) {
            Long eventId = toLong(m.get("event_id"));
            messageMap.put(eventId, TimelineResponse.MessageState.builder()
                    .eventId(eventId)
                    .serverSeq(toLong(m.get("server_seq")))
                    .userId(toLong(m.get("user_id")))
                    .content((String) m.get("content"))
                    .status((String) m.get("status"))
                    .sentAt(parseDateTime(m.get("sent_at")))
                    .build());
        }
    }

    // 이벤트 하나를 상태에 반영
    @SuppressWarnings("unchecked")
    private void applyEvent(Event event,
                            Map<Long, TimelineResponse.ParticipantState> participantMap,
                            Map<Long, TimelineResponse.MessageState> messageMap) {
        Long userId = event.getUser().getId();
        Map<String, Object> payload = event.getPayload();

        switch (event.getEventType()) {
            case JOIN -> participantMap.compute(userId, (id, existing) -> {
                if (existing != null) return TimelineResponse.ParticipantState.builder()
                        .userId(existing.getUserId())
                        .username(existing.getUsername())
                        .status("JOINED")
                        .joinedAt(existing.getJoinedAt())
                        .leftAt(null)
                        .build();
                return TimelineResponse.ParticipantState.builder()
                        .userId(userId)
                        .username(event.getUser().getUsername())
                        .status("JOINED")
                        .joinedAt(event.getServerTs())
                        .build();
            });
            case LEAVE -> {
                TimelineResponse.ParticipantState p = participantMap.get(userId);
                if (p != null) participantMap.put(userId, TimelineResponse.ParticipantState.builder()
                        .userId(p.getUserId()).username(p.getUsername())
                        .status("LEFT").joinedAt(p.getJoinedAt())
                        .leftAt(event.getServerTs()).build());
            }
            case DISCONNECT -> {
                TimelineResponse.ParticipantState p = participantMap.get(userId);
                if (p != null) participantMap.put(userId, TimelineResponse.ParticipantState.builder()
                        .userId(p.getUserId()).username(p.getUsername())
                        .status("DISCONNECTED").joinedAt(p.getJoinedAt())
                        .leftAt(p.getLeftAt()).build());
            }
            case RECONNECT -> {
                TimelineResponse.ParticipantState p = participantMap.get(userId);
                if (p != null) participantMap.put(userId, TimelineResponse.ParticipantState.builder()
                        .userId(p.getUserId()).username(p.getUsername())
                        .status("JOINED").joinedAt(p.getJoinedAt())
                        .leftAt(null).build());
            }
            case MESSAGE -> messageMap.put(event.getId(), TimelineResponse.MessageState.builder()
                    .eventId(event.getId())
                    .serverSeq(event.getServerSeq())
                    .userId(userId)
                    .content((String) payload.get("content"))
                    .status("SENT")
                    .sentAt(event.getServerTs())
                    .build());
            case MESSAGE_EDITED -> {
                String messageId = (String) payload.get("messageId");
                messageMap.entrySet().stream()
                        .filter(e -> String.valueOf(e.getValue().getEventId()).equals(messageId))
                        .findFirst()
                        .ifPresent(e -> messageMap.put(e.getKey(),
                                TimelineResponse.MessageState.builder()
                                        .eventId(e.getValue().getEventId())
                                        .serverSeq(e.getValue().getServerSeq())
                                        .userId(e.getValue().getUserId())
                                        .content((String) payload.get("newContent"))
                                        .status("EDITED")
                                        .sentAt(e.getValue().getSentAt())
                                        .build()));
            }
            case MESSAGE_DELETED -> {
                String messageId = (String) payload.get("messageId");
                messageMap.entrySet().stream()
                        .filter(e -> String.valueOf(e.getValue().getEventId()).equals(messageId))
                        .findFirst()
                        .ifPresent(e -> messageMap.put(e.getKey(),
                                TimelineResponse.MessageState.builder()
                                        .eventId(e.getValue().getEventId())
                                        .serverSeq(e.getValue().getServerSeq())
                                        .userId(e.getValue().getUserId())
                                        .content(e.getValue().getContent())
                                        .status("DELETED")
                                        .sentAt(e.getValue().getSentAt())
                                        .build()));
            }
        }
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.longValue();
        return Long.parseLong(val.toString());
    }

    private LocalDateTime parseDateTime(Object val) {
        if (val == null) return null;
        if (val instanceof LocalDateTime ldt) return ldt;
        return LocalDateTime.parse(val.toString());
    }
}