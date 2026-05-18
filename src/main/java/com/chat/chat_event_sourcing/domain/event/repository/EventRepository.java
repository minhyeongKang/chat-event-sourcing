package com.chat.chat_event_sourcing.domain.event.repository;

import com.chat.chat_event_sourcing.domain.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findBySessionIdAndIdempotencyKey(Long sessionId, String idempotencyKey);

    // 상태 복원용: seq 범위 + 시각 필터
    @Query("""
        SELECT e FROM Event e
        WHERE e.session.id = :sessionId
          AND e.serverSeq > :fromSeq
          AND e.serverTs <= :toTs
        ORDER BY e.serverSeq ASC
    """)
    List<Event> findDeltaEvents(@Param("sessionId") Long sessionId,
                                @Param("fromSeq") Long fromSeq,
                                @Param("toTs") LocalDateTime toTs);

    // 재연결 resume용: fromSeq 이후 이벤트
    @Query("""
        SELECT e FROM Event e
        WHERE e.session.id = :sessionId
          AND e.serverSeq > :fromSeq
        ORDER BY e.serverSeq ASC
    """)
    List<Event> findBySessionIdAndServerSeqAfter(@Param("sessionId") Long sessionId,
                                                 @Param("fromSeq") Long fromSeq);

    // 기준 시각의 server_seq 조회
    @Query("""
        SELECT e.serverSeq FROM Event e
        WHERE e.session.id = :sessionId
          AND e.serverTs <= :ts
        ORDER BY e.serverSeq DESC
    """)
    List<Long> findServerSeqAtTime(@Param("sessionId") Long sessionId,
                                   @Param("ts") LocalDateTime ts);
}