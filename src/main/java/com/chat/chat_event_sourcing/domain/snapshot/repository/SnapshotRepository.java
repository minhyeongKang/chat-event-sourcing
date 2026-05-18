package com.chat.chat_event_sourcing.domain.snapshot.repository;

import com.chat.chat_event_sourcing.domain.snapshot.entity.Snapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {

    // 기준 seq 이하의 가장 최근 스냅샷 조회
    @Query("""
        SELECT s FROM Snapshot s
        WHERE s.session.id = :sessionId
          AND s.atServerSeq <= :seq
        ORDER BY s.atServerSeq DESC
    """)
    List<Snapshot> findLatestBefore(@Param("sessionId") Long sessionId,
                                    @Param("seq") Long seq);

    default Optional<Snapshot> findClosestBefore(Long sessionId, Long seq) {
        List<Snapshot> results = findLatestBefore(sessionId, seq);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    long countBySessionId(Long sessionId);
}