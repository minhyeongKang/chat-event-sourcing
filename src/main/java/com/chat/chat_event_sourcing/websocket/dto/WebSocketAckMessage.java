package com.chat.chat_event_sourcing.websocket.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class WebSocketAckMessage {
    private String idempotencyKey;
    private Long serverSeq;
    private LocalDateTime serverTs;
    private String status;
}