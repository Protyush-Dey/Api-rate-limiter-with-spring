package com.evolving.ratelimiter.algorithm;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Token Bucket Algorithm Implementation
 *
 * Concept:
 * - Bucket holds tokens (max capacity = rate limit)
 * - Tokens refill at constant rate (e.g., 10 per second)
 * - Each request consumes 1 token
 * - If bucket empty, request is rejected (429 Too Many Requests)
 * - Allows brief bursts above rate limit (if tokens accumulated)
 */
public class TokenBucket {

    private final long capacity;          // Maximum tokens bucket can hold
    private final double refillRate;      // Tokens added per second
    private final AtomicLong currentTokens; // Thread-safe token counter
    private volatile long lastRefillTime; // Timestamp of last refill (nanoseconds)

    public TokenBucket(long capacity, double refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.currentTokens = new AtomicLong(capacity); // Start full
        this.lastRefillTime = System.nanoTime();
    }

    /**
     * Attempt to consume a token from the bucket.
     * Refills tokens based on elapsed time first.
     *
     * @return true if token was available (request allowed), false if rejected
     */
    public synchronized boolean tryConsume() {
        refill();
        long tokens = currentTokens.get();
        if (tokens >= 1) {
            currentTokens.decrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * Refill tokens based on elapsed time since last refill.
     * tokensToAdd = elapsedSeconds * refillRate
     */
    private void refill() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillTime;
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        long tokensToAdd = (long) (elapsedSeconds * refillRate);

        if (tokensToAdd > 0) {
            long newTokens = Math.min(capacity, currentTokens.get() + tokensToAdd);
            currentTokens.set(newTokens);
            lastRefillTime = now;
        }
    }

    /**
     * Get current available tokens (for headers)
     */
    public long getAvailableTokens() {
        refill();
        return currentTokens.get();
    }

    /**
     * Get the bucket capacity
     */
    public long getCapacity() {
        return capacity;
    }

    /**
     * Get refill rate (tokens per second)
     */
    public double getRefillRate() {
        return refillRate;
    }

    /**
     * Calculate seconds until next token refill
     */
    public long getSecondsUntilRefill() {
        if (currentTokens.get() >= capacity) return 0;
        return (long) Math.ceil(1.0 / refillRate);
    }

    /**
     * Reset bucket to full capacity (for admin/testing)
     */
    public void reset() {
        currentTokens.set(capacity);
        lastRefillTime = System.nanoTime();
    }
}
