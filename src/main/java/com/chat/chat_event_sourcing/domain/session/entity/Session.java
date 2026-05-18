package com.chat.chat_event_sourcing.domain.session.entity;

import com.chat.chat_event_sourcing.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Session {

    public enum Status { ACTIVE, ENDED, ABORTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "last_event_seq", nullable = false)
    @Builder.Default
    private Long lastEventSeq = 0L;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @PrePersist
    protected void onCreate() {
        this.startedAt = LocalDateTime.now();
        if (this.status == null) this.status = Status.ACTIVE;
    }

    public void end() {
        this.status = Status.ENDED;
        this.endedAt = LocalDateTime.now();
    }

    public void abort() {
        this.status = Status.ABORTED;
        this.endedAt = LocalDateTime.now();
    }

    public void updateLastEventSeq(Long seq) {
        this.lastEventSeq = seq;
    }
}