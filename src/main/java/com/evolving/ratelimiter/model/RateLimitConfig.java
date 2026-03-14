package com.evolving.ratelimiter.model;

import com.evolving.ratelimiter.tier.SubscriptionTier;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private String identifier;         

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private IdentifierType identifierType; 

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SubscriptionTier tier;      

    @Column(nullable = false)
    private long capacity;             

    @Column(nullable = false)
    private double refillRate;          

    @Column(nullable = false)
    private boolean active;

    @Column
    private LocalDateTime tierExpiresAt; 

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
