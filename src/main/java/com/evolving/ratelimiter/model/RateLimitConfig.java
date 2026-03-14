package com.evolving.ratelimiter.model;

import com.evolving.ratelimiter.tier.SubscriptionTier;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Stores per-identifier rate limit configuration.
 * Identifier can be: user_id, IP address, or API key.
 */
@Entity
@Table(name = "rate_limit_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String identifier;          // user_id, IP, or API key value

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private IdentifierType identifierType; // USER, IP, API_KEY

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SubscriptionTier tier;      // FREE, PRO, ENTERPRISE, UNLIMITED

    @Column(nullable = false)
    private long capacity;              // Max tokens

    @Column(nullable = false)
    private double refillRate;          // Tokens per second

    @Column(nullable = false)
    private boolean active;

    @Column
    private LocalDateTime tierExpiresAt; // For temporary tier upgrades

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (active == false && id == null) active = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum IdentifierType {
        USER, IP, API_KEY
    }
}
