package com.evolving.ratelimiter.repository;

import com.evolving.ratelimiter.model.RateLimitEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RateLimitEventRepository extends JpaRepository<RateLimitEvent, Long> {

    List<RateLimitEvent> findByIdentifierOrderByCreatedAtDesc(String identifier);

    long countByIdentifierAndAllowedFalseAndCreatedAtAfter(String identifier, LocalDateTime after);

    long countByCreatedAtAfter(LocalDateTime after);

    long countByAllowedFalseAndCreatedAtAfter(LocalDateTime after);

    @Query("SELECT e.identifier, COUNT(e) as cnt FROM RateLimitEvent e " +
           "WHERE e.allowed = false AND e.createdAt > :after " +
           "GROUP BY e.identifier ORDER BY cnt DESC")
    List<Object[]> findTopViolators(@Param("after") LocalDateTime after);
}
