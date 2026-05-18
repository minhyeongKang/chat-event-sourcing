package com.chat.chat_event_sourcing.api.controller;

import com.chat.chat_event_sourcing.api.dto.TimelineResponse;
import com.chat.chat_event_sourcing.service.TimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class TimelineController {

    private final TimelineService timelineService;

    @GetMapping("/{id}/timeline")
    public ResponseEntity<TimelineResponse> restore(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at) {
        return ResponseEntity.ok(timelineService.restore(id, at));
    }
}