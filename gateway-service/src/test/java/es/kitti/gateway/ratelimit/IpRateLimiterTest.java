package es.kitti.gateway.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IpRateLimiterTest {

    @Test
    void testTimestampCleanupOnExpiredWindow() throws InterruptedException {
        IpRateLimiter limiter = new IpRateLimiter();
        limiter.tryAcquire("cleanup-ip", 10, 1L);
        Thread.sleep(5);
        boolean result = limiter.tryAcquire("cleanup-ip", 10, 1L);
        assertTrue(result);
    }
}
