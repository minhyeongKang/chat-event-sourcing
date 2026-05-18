package com.chat.chat_event_sourcing.api.dto;

import com.chat.chat_event_sourcing.domain.event.entity.Event;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class EventResponse {

    @Getter
    @Builder
    public static class Saved {
        private Long eventId;
        private Long serverSeq;
        private LocalDateTime serverTs;
        private String idempotencyKey;

        public static Saved from(Event event) {
            return Saved.builder()
                    .eventId(event.getId())
                    .serverSeq(event.getServerSeq())
                    .serverTs(event.getServerTs())
                    .idempotencyKey(event.getIdempotencyKey())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class Detail {
        private Long eventId;
        private String eventType;
        private Long userId;
        private Long serverSeq;
        private Long clientSeq;
        private Map<String, Object> payload;
        private LocalDateTime clientTs;
        private LocalDateTime serverTs;

        public static Detail from(Event event) {
            return Detail.builder()
                    .eventId(event.getId())
                    .eventType(event.getEventType().name())
                    .userId(event.getUser().getId())
                    .serverSeq(event.getServerSeq())
                    .clientSeq(event.getClientSeq())
                    .payload(event.getPayload())
                    .clientTs(event.getClientTs())
                    .serverTs(event.getServerTs())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class List_ {
        private java.util.List<Detail> events;
        private Long fromSeq;
        private Long toSeq;
        private boolean hasMore;
    }
}