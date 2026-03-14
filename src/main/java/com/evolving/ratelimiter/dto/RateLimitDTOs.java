package com.evolving.ratelimiter.dto;

import com.evolving.ratelimiter.model.RateLimitConfig;
import com.evolving.ratelimiter.tier.SubscriptionTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class RateLimitDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitCheckRequest {
        @NotBlank(message = "identifier is required")
        private String identifier;

        @NotNull(message = "identifierType is required")
        private RateLimitConfig.IdentifierType identifierType;

        private String endpoint;
        private String httpMethod;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitCheckResponse {
        private boolean allowed;
        private String identifier;
        private long remaining;
        private long limit;
        private long resetInSeconds;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignTierRequest {
        @NotBlank(message = "identifier is required")
        private String identifier;

        @NotNull(message = "identifierType is required")
        private RateLimitConfig.IdentifierType identifierType;

        @NotNull(message = "tier is required")
        private SubscriptionTier tier;

        private LocalDateTime expiresAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TierInfoResponse {
        private String identifier;
        private String tier;
        private long capacity;
        private double refillRate;
        private LocalDateTime tierExpiresAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusResponse {
        private String status;
        private int activeBuckets;
        private long totalRequestsToday;
        private long totalBlockedToday;
        private String message;
    }
}
