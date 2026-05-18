package com.chat.chat_event_sourcing.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
public class EventRequest {
    @NotBlank(message = "이벤트 타입은 필수입니다.")
    private String eventType;

    @NotNull(message = "클라이언트 순서 번호는 필수입니다.")
    private Long clientSeq;

    @NotBlank(message = "멱등성 키는 필수입니다.")
    private String idempotencyKey;

    @NotNull(message = "페이로드는 필수입니다.")
    private Map<String, Object> payload;

    @NotNull(message = "클라이언트 시각은 필수입니다.")
    private LocalDateTime clientTs;
}