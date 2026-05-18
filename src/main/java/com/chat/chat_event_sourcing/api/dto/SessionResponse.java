package com.chat.chat_event_sourcing.api.dto;

import com.chat.chat_event_sourcing.domain.session.entity.Participant;
import com.chat.chat_event_sourcing.domain.session.entity.Session;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;

public class SessionResponse {

    @Getter
    @Builder
    public static class Detail {
        private Long sessionId;
        private String status;
        private Long createdBy;
        private List<ParticipantInfo> participants;
        private Long lastEventSeq;
        private LocalDateTime startedAt;
        private LocalDateTime endedAt;

        public static Detail from(Session session, List<Participant> participants) {
            return Detail.builder()
                    .sessionId(session.getId())
                    .status(session.getStatus().name())
                    .createdBy(session.getCreatedBy().getId())
                    .participants(participants.stream().map(ParticipantInfo::from).toList())
                    .lastEventSeq(session.getLastEventSeq())
                    .startedAt(session.getStartedAt())
                    .endedAt(session.getEndedAt())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class ParticipantInfo {
        private Long userId;
        private String username;
        private String status;
        private LocalDateTime joinedAt;
        private LocalDateTime leftAt;

        public static ParticipantInfo from(Participant p) {
            return ParticipantInfo.builder()
                    .userId(p.getUser().getId())
                    .username(p.getUser().getUsername())
                    .status(p.getStatus().name())
                    .joinedAt(p.getJoinedAt())
                    .leftAt(p.getLeftAt())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class JoinResult {
        private Long sessionId;
        private Long userId;
        private String status;
        private LocalDateTime joinedAt;
        private Long lastEventSeq;
    }

    @Getter
    @Builder
    public static class EndResult {
        private Long sessionId;
        private String status;
        private LocalDateTime endedAt;
        private Long totalEvents;
    }
}