package com.evolving.ratelimiter.controller;

import com.evolving.ratelimiter.repository.RateLimitEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo API endpoints to test the rate limiter filter in action.
 *
 * All these endpoints are automatically rate-limited by RateLimitFilter.
 * Add X-User-ID or X-API-Key headers to test per-user/per-key limiting.
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final RateLimitEventRepository eventRepository;

    /** Simple GET - easy to spam for testing */
    @GetMapping("/hello")
    public ResponseEntity<Map<String, Object>> hello() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Hello! You are within your rate limit.");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("tip", "Add X-User-ID or X-API-Key header to test per-identifier limits");
        return ResponseEntity.ok(response);
    }

    /** POST endpoint - slightly heavier operation */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> process(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Request processed successfully");
        response.put("receivedData", body);
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    /** Returns recent rate limit events for a specific identifier */
    @GetMapping("/events/{identifier}")
    public ResponseEntity<List<?>> getEvents(@PathVariable String identifier) {
        return ResponseEntity.ok(
            eventRepository.findByIdentifierOrderByCreatedAtDesc(identifier)
        );
    }

    /** Analytics: top violators in the last hour */
    @GetMapping("/analytics/violators")
    public ResponseEntity<List<Map<String, Object>>> getTopViolators() {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        List<Object[]> raw = eventRepository.findTopViolators(oneHourAgo);

        List<Map<String, Object>> result = raw.stream().map(row -> {
            Map<String, Object> m = new HashMap<>();
            m.put("identifier", row[0]);
            m.put("violations", row[1]);
            return m;
        }).toList();

        return ResponseEntity.ok(result);
    }
}
