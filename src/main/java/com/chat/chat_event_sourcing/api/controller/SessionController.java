package com.chat.chat_event_sourcing.api.controller;

import com.chat.chat_event_sourcing.api.dto.SessionRequest;
import com.chat.chat_event_sourcing.api.dto.SessionResponse;
import com.chat.chat_event_sourcing.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    // 임시 사용자 ID 헤더 (인증 구현 전 테스트용)
    private Long getCurrentUserId(String userIdHeader) {
        return Long.parseLong(userIdHeader);
    }

    @PostMapping
    public ResponseEntity<SessionResponse.Detail> createSession(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody SessionRequest.Create request) {
        return ResponseEntity.status(201)
                .body(sessionService.createSession(getCurrentUserId(userId), request));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<SessionResponse.JoinResult> joinSession(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(sessionService.joinSession(id, getCurrentUserId(userId)));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<SessionResponse.EndResult> endSession(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(sessionService.endSession(id, getCurrentUserId(userId)));
    }

    @GetMapping
    public ResponseEntity<List<SessionResponse.Detail>> getSessions(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(sessionService.getSessions(
                getCurrentUserId(userId), status, from, to));
    }
}