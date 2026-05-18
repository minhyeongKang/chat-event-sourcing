package com.chat.chat_event_sourcing.service;

import com.chat.chat_event_sourcing.api.dto.SessionRequest;
import com.chat.chat_event_sourcing.api.dto.SessionResponse;
import com.chat.chat_event_sourcing.api.exception.ChatException;
import com.chat.chat_event_sourcing.api.exception.ErrorCode;
import com.chat.chat_event_sourcing.domain.event.repository.EventRepository;
import com.chat.chat_event_sourcing.domain.session.entity.Participant;
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

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Transactional
    public SessionResponse.Detail createSession(Long creatorId, SessionRequest.Create request) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ChatException(ErrorCode.USER_NOT_FOUND));
        User participant = userRepository.findById(request.getParticipantUserId())
                .orElseThrow(() -> new ChatException(ErrorCode.USER_NOT_FOUND));

        if (creatorId.equals(request.getParticipantUserId())) {
            throw new ChatException(ErrorCode.INVALID_REQUEST, "본인과 세션을 생성할 수 없습니다.");
        }

        if (sessionRepository.existsActiveSessionBetween(creatorId, request.getParticipantUserId())) {
            throw new ChatException(ErrorCode.DUPLICATE_SESSION);
        }

        Session session = sessionRepository.save(Session.builder()
                .createdBy(creator)
                .status(Session.Status.ACTIVE)
                .build());

        participantRepository.save(Participant.builder()
                .session(session).user(creator).status(Participant.Status.JOINED).build());
        participantRepository.save(Participant.builder()
                .session(session).user(participant).status(Participant.Status.JOINED).build());

        return SessionResponse.Detail.from(session, participantRepository.findBySession(session));
    }

    @Transactional
    public SessionResponse.JoinResult joinSession(Long sessionId, Long userId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ChatException(ErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() != Session.Status.ACTIVE) {
            throw new ChatException(ErrorCode.SESSION_ENDED);
        }

        Participant participant = participantRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ChatException(ErrorCode.NOT_PARTICIPANT));

        if (participant.getStatus() == Participant.Status.JOINED) {
            throw new ChatException(ErrorCode.ALREADY_JOINED);
        }

        participant.reconnect();

        return SessionResponse.JoinResult.builder()
                .sessionId(sessionId)
                .userId(userId)
                .status(participant.getStatus().name())
                .joinedAt(participant.getJoinedAt())
                .lastEventSeq(session.getLastEventSeq())
                .build();
    }

    @Transactional
    public SessionResponse.EndResult endSession(Long sessionId, Long userId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ChatException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.getCreatedBy().getId().equals(userId)) {
            throw new ChatException(ErrorCode.FORBIDDEN);
        }

        if (session.getStatus() != Session.Status.ACTIVE) {
            throw new ChatException(ErrorCode.SESSION_ENDED);
        }

        session.end();
        long totalEvents = eventRepository.countBySessionId(sessionId);

        return SessionResponse.EndResult.builder()
                .sessionId(sessionId)
                .status(session.getStatus().name())
                .endedAt(session.getEndedAt())
                .totalEvents(totalEvents)
                .build();
    }

    @Transactional(readOnly = true)
    public List<SessionResponse.Detail> getSessions(Long userId, String status,
                                                    LocalDateTime from, LocalDateTime to) {
        Session.Status statusEnum = status != null ? Session.Status.valueOf(status) : null;
        return sessionRepository.findByFilters(userId, statusEnum, from, to)
                .stream()
                .map(s -> SessionResponse.Detail.from(s, participantRepository.findBySession(s)))
                .toList();
    }
}