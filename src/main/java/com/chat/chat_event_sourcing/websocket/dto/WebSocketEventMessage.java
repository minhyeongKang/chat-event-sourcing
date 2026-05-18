package com.chat.chat_event_sourcing.websocket.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class WebSocketEventMessage {
    private String eventType;
    private Long clientSeq;
    private String idempotencyKey;
    private Map<String, Object> payload;
    private LocalDateTime clientTs;
}