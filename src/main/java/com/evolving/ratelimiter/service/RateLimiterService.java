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


@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final RateLimitConfigRepository configRepository;
    private final RateLimitEventRepository eventRepository;
    private final TierConfig tierConfig;


    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();


    public RateLimitResult checkRateLimit(String identifier,
                                          RateLimitConfig.IdentifierType identifierType,
                                          String endpoint,
                                          String httpMethod) {

        TokenBucket bucket = getOrCreateBucket(identifier, identifierType);


        boolean allowed = bucket.tryConsume();
        long remaining = bucket.getAvailableTokens();
        long capacity  = bucket.getCapacity();
        long resetIn   = bucket.getSecondsUntilRefill();


        logEvent(identifier, identifierType.name(), allowed, endpoint, httpMethod, remaining, capacity);

        log.debug("[{}] identifier={} allowed={} remaining={}/{}",
                  httpMethod, identifier, allowed, remaining, capacity);

        return new RateLimitResult(allowed, remaining, capacity, resetIn, identifier);
    }

 
    private TokenBucket getOrCreateBucket(String identifier,
                                           RateLimitConfig.IdentifierType type) {
        return buckets.computeIfAbsent(identifier, id -> {
            RateLimitConfig config = configRepository.findByIdentifier(id)
                .orElseGet(() -> createDefaultConfig(id, type));
            return new TokenBucket(config.getCapacity(), config.getRefillRate());
        });
    }


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


    @Transactional
    public void resetBucket(String identifier) {
        TokenBucket bucket = buckets.get(identifier);
        if (bucket != null) {
            bucket.reset();
            log.info("Bucket reset for identifier: {}", identifier);
        }
    }


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


    @Scheduled(fixedDelay = 300_000) // every 5 minutes
    public void cleanupStaleBuckets() {
        int before = buckets.size();
        // Keep only identifiers that have a DB config
        buckets.keySet().removeIf(id -> !configRepository.findByIdentifier(id).isPresent());
        log.debug("Bucket cleanup: {} -> {} entries", before, buckets.size());
    }


    public Map<String, Long> getBucketStates() {
        Map<String, Long> states = new ConcurrentHashMap<>();
        buckets.forEach((id, bucket) -> states.put(id, bucket.getAvailableTokens()));
        return states;
    }


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


    public record RateLimitResult(
        boolean allowed,
        long remaining,
        long capacity,
        long resetInSeconds,
        String identifier
    ) {}
}
