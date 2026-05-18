package com.chat.chat_event_sourcing.domain.projection.repository;

import com.chat.chat_event_sourcing.domain.projection.entity.Projection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProjectionRepository extends JpaRepository<Projection, Long> {

    Optional<Projection> findBySessionId(Long sessionId);
}