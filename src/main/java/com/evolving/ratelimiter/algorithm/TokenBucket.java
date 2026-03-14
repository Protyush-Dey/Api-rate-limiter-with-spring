package com.evolving.ratelimiter.algorithm;

import java.util.concurrent.atomic.AtomicLong;

public class TokenBucket {

    private final long capacity;
    private final double refillRate;
    private final AtomicLong currentTokens;
    private volatile long lastRefillTime;

    public TokenBucket(long capacity, double refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.currentTokens = new AtomicLong(capacity);
        this.lastRefillTime = System.nanoTime();
    }

    public synchronized boolean tryConsume() {
        refill();
        long tokens = currentTokens.get();
        if (tokens >= 1) {
            currentTokens.decrementAndGet();
            return true;
        }
        return false;
    }

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

    public long getAvailableTokens() {
        refill();
        return currentTokens.get();
    }

    public long getCapacity() {
        return capacity;
    }

    public double getRefillRate() {
        return refillRate;
    }

    public long getSecondsUntilRefill() {
        if (currentTokens.get() >= capacity)
            return 0;
        return (long) Math.ceil(1.0 / refillRate);
    }

    public void reset() {
        currentTokens.set(capacity);
        lastRefillTime = System.nanoTime();
    }
}
