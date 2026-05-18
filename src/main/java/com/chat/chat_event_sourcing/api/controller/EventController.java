package com.chat.chat_event_sourcing.api.controller;

import com.chat.chat_event_sourcing.api.dto.EventRequest;
import com.chat.chat_event_sourcing.api.dto.EventResponse;
import com.chat.chat_event_sourcing.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping("/{id}/events")
    public ResponseEntity<EventResponse.Saved> saveEvent(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long id,
            @Valid @RequestBody EventRequest request) {
        return ResponseEntity.status(201)
                .body(eventService.saveEvent(id, Long.parseLong(userId), request));
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<EventResponse.List_> getEvents(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long id,
            @RequestParam(required = false) Long fromSeq,
            @RequestParam(required = false) Long toSeq,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(
                eventService.getEvents(id, fromSeq, toSeq, from, to, eventType, limit));
    }
}