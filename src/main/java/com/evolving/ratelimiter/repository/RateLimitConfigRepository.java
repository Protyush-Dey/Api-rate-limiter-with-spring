package com.evolving.ratelimiter.repository;

import com.evolving.ratelimiter.model.RateLimitConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RateLimitConfigRepository extends JpaRepository<RateLimitConfig, Long> {

    Optional<RateLimitConfig> findByIdentifier(String identifier);

    List<RateLimitConfig> findByIdentifierType(RateLimitConfig.IdentifierType identifierType);

    List<RateLimitConfig> findByActive(boolean active);
    @Query("SELECT r FROM RateLimitConfig r WHERE r.tierExpiresAt IS NOT NULL AND r.tierExpiresAt < :now")
    List<RateLimitConfig> findExpiredTiers(LocalDateTime now);
}
