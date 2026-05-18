package com.chat.chat_event_sourcing.domain.session.entity;

import com.chat.chat_event_sourcing.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_participants",
                columnNames = {"session_id", "user_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Participant {

    public enum Status { JOINED, LEFT, DISCONNECTED }

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
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @PrePersist
    protected void onCreate() {
        this.joinedAt = LocalDateTime.now();
        if (this.status == null) this.status = Status.JOINED;
    }

    public void leave() {
        this.status = Status.LEFT;
        this.leftAt = LocalDateTime.now();
    }

    public void disconnect() {
        this.status = Status.DISCONNECTED;
    }

    public void reconnect() {
        this.status = Status.JOINED;
    }
}