package com.chat.chat_event_sourcing.api.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ChatException.class)
    public ResponseEntity<Map<String, Object>> handleChatException(ChatException e) {
        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(errorBody(e.getErrorCode().getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("유효하지 않은 요청입니다.");
        return ResponseEntity
                .badRequest()
                .body(errorBody("INVALID_REQUEST", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        return ResponseEntity
                .internalServerError()
                .body(errorBody("INTERNAL_ERROR", "서버 내부 오류입니다."));
    }

    private Map<String, Object> errorBody(String code, String message) {
        return Map.of(
                "error", Map.of(
                        "code", code,
                        "message", message,
                        "timestamp", LocalDateTime.now().toString()
                )
        );
    }
}