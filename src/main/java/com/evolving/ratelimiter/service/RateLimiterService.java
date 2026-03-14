package com.evolving.ratelimiter.service;

import com.evolving.ratelimiter.algorithm.TokenBucket;
import com.evolving.ratelimiter.config.TierConfig;
import com.evolving.ratelimiter.model.RateLimitConfig;
import com.evolving.ratelimiter.model.RateLimitEvent;
import com.evolving.ratelimiter.repository.RateLimitConfigRepository;
import com.evolving.ratelimiter.repository.RateLimitEventRepository;
import com.evolving.ratelimiter.tier.SubscriptionTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core Rate Limiter Service.
 *
 * Uses in-memory ConcurrentHashMap for O(1) bucket lookup.
 * Buckets are lazily created on first request per identifier.
 * Configuration is persisted to DB; buckets live in memory.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final RateLimitConfigRepository configRepository;
    private final RateLimitEventRepository eventRepository;
    private final TierConfig tierConfig;

    // In-memory bucket store: identifier -> TokenBucket
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /**
     * Main rate limit check.
     * Returns a RateLimitResult with decision + headers info.
     */
    public RateLimitResult checkRateLimit(String identifier,
                                          RateLimitConfig.IdentifierType identifierType,
                                          String endpoint,
                                          String httpMethod) {
        // 1. Get or create bucket for this identifier
        TokenBucket bucket = getOrCreateBucket(identifier, identifierType);

        // 2. Try to consume a token
        boolean allowed = bucket.tryConsume();
        long remaining = bucket.getAvailableTokens();
        long capacity  = bucket.getCapacity();
        long resetIn   = bucket.getSecondsUntilRefill();

        // 3. Async log event
        logEvent(identifier, identifierType.name(), allowed, endpoint, httpMethod, remaining, capacity);

        log.debug("[{}] identifier={} allowed={} remaining={}/{}",
                  httpMethod, identifier, allowed, remaining, capacity);

        return new RateLimitResult(allowed, remaining, capacity, resetIn, identifier);
    }

    /**
     * Get existing bucket or create a new one from config/defaults.
     */
    private TokenBucket getOrCreateBucket(String identifier,
                                           RateLimitConfig.IdentifierType type) {
        return buckets.computeIfAbsent(identifier, id -> {
            RateLimitConfig config = configRepository.findByIdentifier(id)
                .orElseGet(() -> createDefaultConfig(id, type));
            return new TokenBucket(config.getCapacity(), config.getRefillRate());
        });
    }

    /**
     * Create a default FREE tier config when identifier is seen for the first time.
     */
    @Transactional
    private RateLimitConfig createDefaultConfig(String identifier,
                                                 RateLimitConfig.IdentifierType type) {
        TierConfig.TierLimits limits = tierConfig.getLimitsForTier(SubscriptionTier.FREE);
        RateLimitConfig config = RateLimitConfig.builder()
            .identifier(identifier)
            .identifierType(type)
            .tier(SubscriptionTier.FREE)
            .capacity(limits.capacity())
            .refillRate(limits.refillRate())
            .active(true)
            .build();
        return configRepository.save(config);
    }

    /**
     * Manually reset a bucket (admin/testing endpoint).
     */
    @Transactional
    public void resetBucket(String identifier) {
        TokenBucket bucket = buckets.get(identifier);
        if (bucket != null) {
            bucket.reset();
            log.info("Bucket reset for identifier: {}", identifier);
        }
    }

    /**
     * Assign or upgrade tier for an identifier.
     */
    @Transactional
    public RateLimitConfig assignTier(String identifier,
                                       RateLimitConfig.IdentifierType type,
                                       SubscriptionTier tier,
                                       LocalDateTime expiresAt) {
        TierConfig.TierLimits limits = tierConfig.getLimitsForTier(tier);

        RateLimitConfig config = configRepository.findByIdentifier(identifier)
            .orElseGet(() -> RateLimitConfig.builder()
                .identifier(identifier)
                .identifierType(type)
                .active(true)
                .build());

        config.setTier(tier);
        config.setCapacity(limits.capacity());
        config.setRefillRate(limits.refillRate());
        config.setTierExpiresAt(expiresAt);
        RateLimitConfig saved = configRepository.save(config);

        // Rebuild bucket with new limits
        buckets.put(identifier, new TokenBucket(limits.capacity(), limits.refillRate()));
        log.info("Tier {} assigned to identifier: {} (expires: {})", tier, identifier, expiresAt);

        return saved;
    }

    /**
     * Scheduled job: expire temporary tier upgrades, downgrade to FREE.
     * Runs every 60 seconds.
     */
    @Scheduled(fixedDelayString = "${ratelimiter.cleanup.interval-ms:60000}")
    @Transactional
    public void expireTemporaryTiers() {
        LocalDateTime now = LocalDateTime.now();
        configRepository.findExpiredTiers(now).forEach(config -> {
            log.info("Tier expired for {}. Downgrading to FREE.", config.getIdentifier());
            TierConfig.TierLimits limits = tierConfig.getLimitsForTier(SubscriptionTier.FREE);
            config.setTier(SubscriptionTier.FREE);
            config.setCapacity(limits.capacity());
            config.setRefillRate(limits.refillRate());
            config.setTierExpiresAt(null);
            configRepository.save(config);
            buckets.put(config.getIdentifier(),
                        new TokenBucket(limits.capacity(), limits.refillRate()));
        });
    }

    /**
     * Cleanup old buckets not seen recently (memory management).
     */
    @Scheduled(fixedDelay = 300_000) // every 5 minutes
    public void cleanupStaleBuckets() {
        int before = buckets.size();
        // Keep only identifiers that have a DB config
        buckets.keySet().removeIf(id -> !configRepository.findByIdentifier(id).isPresent());
        log.debug("Bucket cleanup: {} -> {} entries", before, buckets.size());
    }

    /**
     * Get all current bucket states (for admin/status).
     */
    public Map<String, Long> getBucketStates() {
        Map<String, Long> states = new ConcurrentHashMap<>();
        buckets.forEach((id, bucket) -> states.put(id, bucket.getAvailableTokens()));
        return states;
    }

    /**
     * Log rate limit event asynchronously (best-effort).
     */
    private void logEvent(String identifier, String identifierType,
                           boolean allowed, String endpoint, String method,
                           long remaining, long capacity) {
        try {
            eventRepository.save(RateLimitEvent.builder()
                .identifier(identifier)
                .identifierType(identifierType)
                .allowed(allowed)
                .endpoint(endpoint)
                .httpMethod(method)
                .tokensRemaining(remaining)
                .capacity(capacity)
                .build());
        } catch (Exception e) {
            log.warn("Failed to log rate limit event: {}", e.getMessage());
        }
    }

    /**
     * Result object returned from rate limit check.
     */
    public record RateLimitResult(
        boolean allowed,
        long remaining,
        long capacity,
        long resetInSeconds,
        String identifier
    ) {}
}
