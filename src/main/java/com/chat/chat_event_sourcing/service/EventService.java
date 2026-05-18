package com.chat.chat_event_sourcing.service;

import com.chat.chat_event_sourcing.api.dto.EventRequest;
import com.chat.chat_event_sourcing.api.dto.EventResponse;
import com.chat.chat_event_sourcing.api.exception.ChatException;
import com.chat.chat_event_sourcing.api.exception.ErrorCode;
import com.chat.chat_event_sourcing.domain.event.entity.Event;
import com.chat.chat_event_sourcing.domain.event.repository.EventRepository;
import com.chat.chat_event_sourcing.domain.session.entity.Session;
import com.chat.chat_event_sourcing.domain.session.repository.ParticipantRepository;
import com.chat.chat_event_sourcing.domain.session.repository.SessionRepository;
import com.chat.chat_event_sourcing.domain.user.entity.User;
import com.chat.chat_event_sourcing.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;

    @Transactional
    public EventResponse.Saved saveEvent(Long sessionId, Long userId, EventRequest request) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ChatException(ErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() != Session.Status.ACTIVE) {
            throw new ChatException(ErrorCode.SESSION_ENDED);
        }

        if (!participantRepository.existsBySessionIdAndUserId(sessionId, userId)) {
            throw new ChatException(ErrorCode.NOT_PARTICIPANT);
        }

        // 중복 이벤트 확인 (멱등성)
        Optional<Event> existing = eventRepository
                .findBySessionIdAndIdempotencyKey(sessionId, request.getIdempotencyKey());
        if (existing.isPresent()) {
            return EventResponse.Saved.from(existing.get());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ChatException(ErrorCode.USER_NOT_FOUND));

        // server_seq 발급 (세션의 last_event_seq + 1)
        long serverSeq = session.getLastEventSeq() + 1;
        session.updateLastEventSeq(serverSeq);

        Event event = eventRepository.save(Event.builder()
                .session(session)
                .user(user)
                .eventType(Event.Type.valueOf(request.getEventType()))
                .clientSeq(request.getClientSeq())
                .serverSeq(serverSeq)
                .idempotencyKey(request.getIdempotencyKey())
                .payload(request.getPayload())
                .clientTs(request.getClientTs())
                .build());

        return EventResponse.Saved.from(event);
    }

    @Transactional(readOnly = true)
    public EventResponse.List_ getEvents(Long sessionId, Long fromSeq, Long toSeq,
                                         LocalDateTime from, LocalDateTime to,
                                         String eventType, int limit) {
        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ChatException(ErrorCode.SESSION_NOT_FOUND));

        List<Event> events;
        if (fromSeq != null) {
            events = eventRepository.findBySessionIdAndServerSeqAfter(sessionId, fromSeq);
        } else if (from != null && to != null) {
            events = eventRepository.findDeltaEvents(sessionId, 0L, to);
        } else {
            events = eventRepository.findBySessionIdAndServerSeqAfter(sessionId, 0L);
        }

        // limit 적용
        boolean hasMore = events.size() > limit;
        if (hasMore) events = events.subList(0, limit);

        List<EventResponse.Detail> details = events.stream()
                .map(EventResponse.Detail::from)
                .toList();

        Long minSeq = details.isEmpty() ? null : details.get(0).getServerSeq();
        Long maxSeq = details.isEmpty() ? null : details.get(details.size() - 1).getServerSeq();

        return EventResponse.List_.builder()
                .events(details)
                .fromSeq(minSeq)
                .toSeq(maxSeq)
                .hasMore(hasMore)
                .build();
    }
}