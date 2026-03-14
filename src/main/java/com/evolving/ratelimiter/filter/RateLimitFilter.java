package com.evolving.ratelimiter.filter;

import com.evolving.ratelimiter.model.RateLimitConfig;
import com.evolving.ratelimiter.service.RateLimiterService;
import com.evolving.ratelimiter.service.RateLimiterService.RateLimitResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP Filter that automatically rate limits ALL incoming requests by IP address.
 *
 * This acts as middleware — every request passes through here.
 * Admin endpoints (/api/admin/**) and internal endpoints are excluded.
 *
 * Rate limit headers are added to every response:
 *   X-RateLimit-Limit:     Maximum requests allowed
 *   X-RateLimit-Remaining: Tokens remaining
 *   X-RateLimit-Reset:     Seconds until reset
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    // Paths excluded from rate limiting
    private static final String[] EXCLUDED_PATHS = {
        "/api/admin",
        "/api/rate-limit",
        "/h2-console",
        "/actuator",
        "/favicon.ico"
    };

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String excluded : EXCLUDED_PATHS) {
            if (path.startsWith(excluded)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String ip = getClientIp(request);
        String identifier = resolveIdentifier(request, ip);
        RateLimitConfig.IdentifierType type = resolveIdentifierType(request);

        RateLimitResult result = rateLimiterService.checkRateLimit(
            identifier, type,
            request.getRequestURI(),
            request.getMethod()
        );

        // Always add rate limit headers
        response.setHeader("X-RateLimit-Limit",     String.valueOf(result.capacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset",     String.valueOf(result.resetInSeconds()));

        if (!result.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(result.resetInSeconds()));

            Map<String, Object> body = new HashMap<>();
            body.put("error", "Too Many Requests");
            body.put("message", "Rate limit exceeded. Retry after " + result.resetInSeconds() + " seconds.");
            body.put("identifier", identifier);
            body.put("retryAfterSeconds", result.resetInSeconds());
            body.put("timestamp", LocalDateTime.now().toString());

            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveIdentifier(HttpServletRequest request, String ip) {
        // Priority: X-API-Key header > X-User-ID header > IP
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) return "apikey:" + apiKey;

        String userId = request.getHeader("X-User-ID");
        if (userId != null && !userId.isBlank()) return "user:" + userId;

        return "ip:" + ip;
    }

    private RateLimitConfig.IdentifierType resolveIdentifierType(HttpServletRequest request) {
        if (request.getHeader("X-API-Key") != null) return RateLimitConfig.IdentifierType.API_KEY;
        if (request.getHeader("X-User-ID") != null)  return RateLimitConfig.IdentifierType.USER;
        return RateLimitConfig.IdentifierType.IP;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
