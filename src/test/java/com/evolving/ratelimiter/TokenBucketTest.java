package com.evolving.ratelimiter;

import com.evolving.ratelimiter.algorithm.TokenBucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Token Bucket Algorithm Tests")
class TokenBucketTest {

    private TokenBucket bucket;

    @BeforeEach
    void setUp() {

        bucket = new TokenBucket(100, 10.0);
    }

    @Test
    @DisplayName("Bucket should start with full capacity")
    void testBucketStartsFull() {
        assertEquals(100, bucket.getAvailableTokens());
        assertEquals(100, bucket.getCapacity());
    }

    @Test
    @DisplayName("First 100 requests should all be allowed")
    void testFirst100RequestsSucceed() {
        int successCount = 0;
        for (int i = 0; i < 100; i++) {
            if (bucket.tryConsume())
                successCount++;
        }
        assertEquals(100, successCount, "All 100 requests should succeed");
    }

    @Test
    @DisplayName("101st request should be rejected when bucket is empty")
    void test101stRequestFails() {
        for (int i = 0; i < 100; i++) {
            bucket.tryConsume();
        }
        assertFalse(bucket.tryConsume(), "101st request should be rejected");
        assertEquals(0, bucket.getAvailableTokens());
    }

    @Test
    @DisplayName("120 requests: first 100 succeed, next 20 fail")
    void test120RequestsPartialSuccess() {
        int allowed = 0;
        int rejected = 0;
        for (int i = 0; i < 120; i++) {
            if (bucket.tryConsume())
                allowed++;
            else
                rejected++;
        }
        assertEquals(100, allowed, "Exactly 100 should be allowed");
        assertEquals(20, rejected, "Exactly 20 should be rejected");
    }

    @Test
    @DisplayName("Reset should restore bucket to full capacity")
    void testResetRestoresBucket() {

        for (int i = 0; i < 100; i++)
            bucket.tryConsume();
        assertEquals(0, bucket.getAvailableTokens());

        bucket.reset();
        assertEquals(100, bucket.getAvailableTokens());
        assertTrue(bucket.tryConsume(), "Request should succeed after reset");
    }

    @Test
    @DisplayName("Tokens should refill after waiting")
    void testTokensRefillOverTime() throws InterruptedException {

        for (int i = 0; i < 100; i++)
            bucket.tryConsume();
        assertEquals(0, bucket.getAvailableTokens());

        Thread.sleep(2100);

        long available = bucket.getAvailableTokens();
        assertTrue(available >= 18, "Should have refilled ~20 tokens, got: " + available);
        assertTrue(bucket.tryConsume(), "Should be allowed after refill");
    }

    @Test
    @DisplayName("Different buckets should track independently")
    void testDifferentBucketsAreIndependent() {
        TokenBucket bucket1 = new TokenBucket(10, 1.0);
        TokenBucket bucket2 = new TokenBucket(10, 1.0);

        for (int i = 0; i < 10; i++)
            bucket1.tryConsume();

        assertEquals(0, bucket1.getAvailableTokens());
        assertEquals(10, bucket2.getAvailableTokens());
        assertFalse(bucket1.tryConsume(), "bucket1 should be exhausted");
        assertTrue(bucket2.tryConsume(), "bucket2 should still work");
    }

    @Test
    @DisplayName("Tokens should not exceed maximum capacity")
    void testTokensDoNotExceedCapacity() throws InterruptedException {

        Thread.sleep(500);
        assertTrue(bucket.getAvailableTokens() <= 100,
                "Available tokens should never exceed capacity");
    }

    @Test
    @DisplayName("Concurrent requests should be handled safely")
    void testConcurrentAccess() throws InterruptedException {
        TokenBucket concurrentBucket = new TokenBucket(100, 10.0);
        int[] successCount = { 0 };
        int threadCount = 20;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    if (concurrentBucket.tryConsume()) {
                        synchronized (successCount) {
                            successCount[0]++;
                        }
                    }
                }
            });
        }

        for (Thread t : threads)
            t.start();
        for (Thread t : threads)
            t.join();

        assertTrue(successCount[0] <= 100,
                "No more than 100 requests should succeed, got: " + successCount[0]);
    }

    @Test
    @DisplayName("Refill rate should be accurate")
    void testRefillRateAccuracy() {
        assertEquals(10.0, bucket.getRefillRate(), "Refill rate should be 10 tokens/sec");
    }
}
