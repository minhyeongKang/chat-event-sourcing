package com.chat.chat_event_sourcing.domain.session.repository;

import com.chat.chat_event_sourcing.domain.session.entity.Participant;
import com.chat.chat_event_sourcing.domain.session.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    List<Participant> findBySession(Session session);

    Optional<Participant> findBySessionIdAndUserId(Long sessionId, Long userId);

    boolean existsBySessionIdAndUserId(Long sessionId, Long userId);
}