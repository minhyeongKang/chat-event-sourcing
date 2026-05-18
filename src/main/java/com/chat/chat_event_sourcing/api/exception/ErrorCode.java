package com.chat.chat_event_sourcing.api.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "잘못된 요청입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "세션을 찾을 수 없습니다."),
    DUPLICATE_SESSION(HttpStatus.CONFLICT, "DUPLICATE_SESSION", "이미 진행 중인 세션이 있습니다."),
    ALREADY_JOINED(HttpStatus.CONFLICT, "ALREADY_JOINED", "이미 참여 중인 세션입니다."),
    SESSION_ENDED(HttpStatus.GONE, "SESSION_ENDED", "종료된 세션입니다."),
    NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "FORBIDDEN", "세션 참여자가 아닙니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 내부 오류입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}