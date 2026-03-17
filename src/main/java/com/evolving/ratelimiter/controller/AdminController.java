package com.evolving.ratelimiter.controller;

import com.evolving.ratelimiter.config.TierConfig;
import com.evolving.ratelimiter.dto.RateLimitDTOs.*;
import com.evolving.ratelimiter.model.RateLimitConfig;
import com.evolving.ratelimiter.repository.RateLimitConfigRepository;
import com.evolving.ratelimiter.service.RateLimiterService;
import com.evolving.ratelimiter.tier.SubscriptionTier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final RateLimiterService rateLimiterService;
    private final RateLimitConfigRepository configRepository;
    private final TierConfig tierConfig;

    @PostMapping("/tier/assign")
    public ResponseEntity<TierInfoResponse> assignTier(
            @Valid @RequestBody AssignTierRequest request) {

        RateLimitConfig saved = rateLimiterService.assignTier(
            request.getIdentifier(),
            request.getIdentifierType(),
            request.getTier(),
            request.getExpiresAt()
        );

        TierInfoResponse response = TierInfoResponse.builder()
            .identifier(saved.getIdentifier())
            .tier(saved.getTier().name())
            .capacity(saved.getCapacity())
            .refillRate(saved.getRefillRate())
            .tierExpiresAt(saved.getTierExpiresAt())
            .updatedAt(saved.getUpdatedAt())
            .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/tier/{identifier}")
    public ResponseEntity<TierInfoResponse> getTierInfo(@PathVariable String identifier) {
        return configRepository.findByIdentifier(identifier)
            .map(config -> ResponseEntity.ok(TierInfoResponse.builder()
                .identifier(config.getIdentifier())
                .tier(config.getTier().name())
                .capacity(config.getCapacity())
                .refillRate(config.getRefillRate())
                .tierExpiresAt(config.getTierExpiresAt())
                .updatedAt(config.getUpdatedAt())
                .build()))
            .orElse(ResponseEntity.notFound().build());
    }


    @GetMapping("/tiers")
    public ResponseEntity<Map<String, TierConfig.TierLimits>> getAllTiers() {
        return ResponseEntity.ok(tierConfig.getAllTierLimits());
    }

    @GetMapping("/configs")
    public ResponseEntity<List<RateLimitConfig>> getAllConfigs() {
        return ResponseEntity.ok(configRepository.findAll());
    }


    @GetMapping("/configs/type/{type}")
    public ResponseEntity<List<RateLimitConfig>> getConfigsByType(
            @PathVariable RateLimitConfig.IdentifierType type) {
        return ResponseEntity.ok(configRepository.findByIdentifierType(type));
    }


    @DeleteMapping("/config/{identifier}")
    public ResponseEntity<String> deleteConfig(@PathVariable String identifier) {
        return configRepository.findByIdentifier(identifier)
            .map(config -> {
                configRepository.delete(config);
                rateLimiterService.resetBucket(identifier);
                return ResponseEntity.ok("Config deleted for: " + identifier);
            })
            .orElse(ResponseEntity.notFound().build());
    }


    @PostMapping("/tier/upgrade")
    public ResponseEntity<TierInfoResponse> upgradeTier(
            @RequestParam String identifier,
            @RequestParam(defaultValue = "USER") RateLimitConfig.IdentifierType type,
            @RequestParam SubscriptionTier newTier) {

        RateLimitConfig saved = rateLimiterService.assignTier(identifier, type, newTier, null);

        return ResponseEntity.ok(TierInfoResponse.builder()
            .identifier(saved.getIdentifier())
            .tier(saved.getTier().name())
            .capacity(saved.getCapacity())
            .refillRate(saved.getRefillRate())
            .tierExpiresAt(saved.getTierExpiresAt())
            .updatedAt(saved.getUpdatedAt())
            .build());
    }

    @GetMapping("/buckets")
    public ResponseEntity<Map<String, Long>> getBucketStates() {
        return ResponseEntity.ok(rateLimiterService.getBucketStates());
    }
}
