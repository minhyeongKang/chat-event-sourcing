package com.chat.chat_event_sourcing.api.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class TimelineResponse {

    private Long sessionId;
    private LocalDateTime restoredAt;
    private String replayStrategy;
    private Long snapshotSeq;
    private int replayedEventCount;
    private State state;

    @Getter
    @Builder
    public static class State {
        private String sessionStatus;
        private List<ParticipantState> participants;
        private List<MessageState> messages;
    }

    @Getter
    @Builder
    public static class ParticipantState {
        private Long userId;
        private String username;
        private String status;
        private LocalDateTime joinedAt;
        private LocalDateTime leftAt;
    }

    @Getter
    @Builder
    public static class MessageState {
        private Long eventId;
        private Long serverSeq;
        private Long userId;
        private String content;
        private String status;
        private LocalDateTime sentAt;
    }
}