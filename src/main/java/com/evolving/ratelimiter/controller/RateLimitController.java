package com.evolving.ratelimiter.controller;

import com.evolving.ratelimiter.dto.RateLimitDTOs.*;
import com.evolving.ratelimiter.model.RateLimitConfig;
import com.evolving.ratelimiter.repository.RateLimitEventRepository;
import com.evolving.ratelimiter.service.RateLimiterService;
import com.evolving.ratelimiter.service.RateLimiterService.RateLimitResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Core Rate Limit API Endpoints.
 *
 * POST /api/rate-limit/check      → Check if request is allowed
 * GET  /api/rate-limit/status     → Service health and stats
 * POST /api/rate-limit/reset      → Reset a specific identifier's bucket (testing)
 */
@RestController
@RequestMapping("/api/rate-limit")
@RequiredArgsConstructor
public class RateLimitController {

    private final RateLimiterService rateLimiterService;
    private final RateLimitEventRepository eventRepository;

    /**
     * Primary endpoint: Check if a request should be allowed.
     *
     * Returns 200 if allowed, 429 if rate limit exceeded.
     * Always includes X-RateLimit-* headers.
     */
    @PostMapping("/check")
    public ResponseEntity<RateLimitCheckResponse> checkRateLimit(
            @Valid @RequestBody RateLimitCheckRequest request) {

        RateLimitResult result = rateLimiterService.checkRateLimit(
            request.getIdentifier(),
            request.getIdentifierType(),
            request.getEndpoint(),
            request.getHttpMethod()
        );

        HttpHeaders headers = buildRateLimitHeaders(result);

        if (result.allowed()) {
            RateLimitCheckResponse response = RateLimitCheckResponse.builder()
                .allowed(true)
                .identifier(result.identifier())
                .remaining(result.remaining())
                .limit(result.capacity())
                .resetInSeconds(result.resetInSeconds())
                .message("Request allowed")
                .build();
            return ResponseEntity.ok().headers(headers).body(response);
        } else {
            RateLimitCheckResponse response = RateLimitCheckResponse.builder()
                .allowed(false)
                .identifier(result.identifier())
                .remaining(0)
                .limit(result.capacity())
                .resetInSeconds(result.resetInSeconds())
                .message("Rate limit exceeded. Retry after " + result.resetInSeconds() + " seconds.")
                .build();
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(response);
        }
    }

    /**
     * Check rate limit using IP address from incoming request.
     * Simpler endpoint for middleware-style usage.
     */
    @GetMapping("/check-ip")
    public ResponseEntity<RateLimitCheckResponse> checkByIp(
            HttpServletRequest servletRequest) {

        String ip = getClientIp(servletRequest);
        RateLimitResult result = rateLimiterService.checkRateLimit(
            ip,
            RateLimitConfig.IdentifierType.IP,
            servletRequest.getRequestURI(),
            servletRequest.getMethod()
        );

        HttpHeaders headers = buildRateLimitHeaders(result);

        if (result.allowed()) {
            return ResponseEntity.ok().headers(headers)
                .body(RateLimitCheckResponse.builder()
                    .allowed(true).identifier(ip)
                    .remaining(result.remaining()).limit(result.capacity())
                    .message("Request allowed").build());
        } else {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(headers)
                .body(RateLimitCheckResponse.builder()
                    .allowed(false).identifier(ip)
                    .remaining(0).limit(result.capacity())
                    .resetInSeconds(result.resetInSeconds())
                    .message("Rate limit exceeded").build());
        }
    }

    /**
     * Status endpoint - service health and statistics.
     */
    @GetMapping("/status")
    public ResponseEntity<StatusResponse> getStatus() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        long totalToday   = eventRepository.countByCreatedAtAfter(startOfDay);
        long blockedToday = eventRepository.countByAllowedFalseAndCreatedAtAfter(startOfDay);

        StatusResponse response = StatusResponse.builder()
            .status("UP")
            .activeBuckets(rateLimiterService.getBucketStates().size())
            .totalRequestsToday(totalToday)
            .totalBlockedToday(blockedToday)
            .message("Rate Limiter Service is running")
            .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Reset endpoint - clear bucket for an identifier (for testing).
     * POST /api/rate-limit/reset?identifier=user_123
     */
    @PostMapping("/reset")
    public ResponseEntity<String> resetBucket(
            @RequestParam String identifier) {
        rateLimiterService.resetBucket(identifier);
        return ResponseEntity.ok("Bucket reset for identifier: " + identifier);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private HttpHeaders buildRateLimitHeaders(RateLimitResult result) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Limit",     String.valueOf(result.capacity()));
        headers.add("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        headers.add("X-RateLimit-Reset",     String.valueOf(result.resetInSeconds()));
        if (!result.allowed()) {
            headers.add("Retry-After", String.valueOf(result.resetInSeconds()));
        }
        return headers;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
