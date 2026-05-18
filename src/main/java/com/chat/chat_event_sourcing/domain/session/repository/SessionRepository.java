package com.chat.chat_event_sourcing.domain.session.repository;

import com.chat.chat_event_sourcing.domain.session.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface SessionRepository extends JpaRepository<Session, Long> {

    // 두 사용자 간 ACTIVE 세션 존재 여부 확인 (중복 세션 방지)
    @Query("""
        SELECT COUNT(s) > 0 FROM Session s
        JOIN Participant p1 ON p1.session = s AND p1.user.id = :userId1
        JOIN Participant p2 ON p2.session = s AND p2.user.id = :userId2
        WHERE s.status = 'ACTIVE'
    """)
    boolean existsActiveSessionBetween(@Param("userId1") Long userId1,
                                       @Param("userId2") Long userId2);

    // 세션 목록 조회 (참여자 + 상태 + 기간 필터)
    @Query("""
        SELECT DISTINCT s FROM Session s
        JOIN Participant p ON p.session = s
        WHERE (:userId IS NULL OR p.user.id = :userId)
          AND (:status IS NULL OR s.status = :status)
          AND (:from IS NULL OR s.startedAt >= :from)
          AND (:to IS NULL OR s.startedAt < :to)
        ORDER BY s.startedAt DESC
    """)
    List<Session> findByFilters(@Param("userId") Long userId,
                                @Param("status") Session.Status status,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);
}