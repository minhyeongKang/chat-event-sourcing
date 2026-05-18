package com.chat.chat_event_sourcing.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

public class SessionRequest {

    @Getter
    public static class Create {
        @NotNull(message = "참여자 ID는 필수입니다.")
        private Long participantUserId;
    }
}