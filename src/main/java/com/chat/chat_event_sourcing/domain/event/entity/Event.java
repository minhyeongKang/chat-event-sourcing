package com.chat.chat_event_sourcing.domain.event.entity;

import com.chat.chat_event_sourcing.domain.session.entity.Session;
import com.chat.chat_event_sourcing.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_events_idempotency",  columnNames = {"session_id", "idempotency_key"}),
                @UniqueConstraint(name = "uq_events_server_seq",   columnNames = {"session_id", "server_seq"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Event {

    public enum Type {
        MESSAGE, JOIN, LEAVE, DISCONNECT, RECONNECT, MESSAGE_EDITED, MESSAGE_DELETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private Type eventType;

    @Column(name = "client_seq", nullable = false)
    private Long clientSeq;

    @Column(name = "server_seq", nullable = false)
    private Long serverSeq;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private Map<String, Object> payload;

    @Column(name = "client_ts", nullable = false)
    private LocalDateTime clientTs;

    @Column(name = "server_ts", nullable = false)
    private LocalDateTime serverTs;

    @PrePersist
    protected void onCreate() {
        this.serverTs = LocalDateTime.now();
    }
}