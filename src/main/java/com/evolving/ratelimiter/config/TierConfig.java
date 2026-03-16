package com.evolving.ratelimiter.config;

import com.evolving.ratelimiter.tier.SubscriptionTier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.HashMap;

@Component
public class TierConfig {

    @Value("${ratelimiter.tier.free.capacity:100}")
    private long freeCapacity;
    @Value("${ratelimiter.tier.free.refill-rate:10}")
    private double freeRefillRate;

    @Value("${ratelimiter.tier.pro.capacity:1000}")
    private long proCapacity;
    @Value("${ratelimiter.tier.pro.refill-rate:100}")
    private double proRefillRate;

    @Value("${ratelimiter.tier.enterprise.capacity:10000}")
    private long enterpriseCapacity;
    @Value("${ratelimiter.tier.enterprise.refill-rate:1000}")
    private double enterpriseRefillRate;

    @Value("${ratelimiter.tier.unlimited.capacity:999999999}")
    private long unlimitedCapacity;
    @Value("${ratelimiter.tier.unlimited.refill-rate:999999999}")
    private double unlimitedRefillRate;

    public record TierLimits(long capacity, double refillRate) {
    }

    public TierLimits getLimitsForTier(SubscriptionTier tier) {
        return switch (tier) {
            case FREE -> new TierLimits(freeCapacity, freeRefillRate);
            case PRO -> new TierLimits(proCapacity, proRefillRate);
            case ENTERPRISE -> new TierLimits(enterpriseCapacity, enterpriseRefillRate);
            case UNLIMITED -> new TierLimits(unlimitedCapacity, unlimitedRefillRate);
        };
    }

    public Map<String, TierLimits> getAllTierLimits() {
        Map<String, TierLimits> map = new HashMap<>();
        for (SubscriptionTier tier : SubscriptionTier.values()) {
            map.put(tier.name(), getLimitsForTier(tier));
        }
        return map;
    }
}
