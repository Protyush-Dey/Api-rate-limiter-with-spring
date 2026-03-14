package com.evolving.ratelimiter.exception;

public class RateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(String identifier, long retryAfterSeconds) {
        super("Rate limit exceeded for: " + identifier);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
