package com.evolving.ratelimiter.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tracks every rate limit check for analytics and auditing.
 */
@Entity
@Table(name = "rate_limit_events",
       indexes = {
           @Index(name = "idx_identifier", columnList = "identifier"),
           @Index(name = "idx_created_at", columnList = "createdAt")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String identifier;

    @Column(nullable = false)
    private String identifierType;

    @Column(nullable = false)
    private boolean allowed;           // true = allowed, false = rejected (429)

    @Column
    private String endpoint;           // Which API endpoint was called

    @Column
    private String httpMethod;

    @Column(nullable = false)
    private long tokensRemaining;

    @Column(nullable = false)
    private long capacity;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
