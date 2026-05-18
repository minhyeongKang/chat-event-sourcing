package com.chat.chat_event_sourcing.domain.projection.entity;

import com.chat.chat_event_sourcing.domain.session.entity.Session;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "projections",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_projections_session",
                columnNames = {"session_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Projection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(name = "last_event_seq", nullable = false)
    @Builder.Default
    private Long lastEventSeq = 0L;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_state", nullable = false, columnDefinition = "json")
    private Map<String, Object> currentState;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateState(Map<String, Object> newState, Long newSeq) {
        this.currentState = newState;
        this.lastEventSeq = newSeq;
    }
}