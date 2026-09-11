package com.example.waterrefillapijava;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.waterrefillapijava.security.SlidingWindowRateLimiter;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimiterTest {

	@Autowired
	private SlidingWindowRateLimiter slidingWindowRateLimiter;

	@Test
	void rateLimiterThrottlesAndResets() {
		final String key = "test-rate-limit";

		assertFalse(slidingWindowRateLimiter.isRateLimited(key, 3, 60000));
		assertFalse(slidingWindowRateLimiter.isRateLimited(key, 3, 60000));
		assertFalse(slidingWindowRateLimiter.isRateLimited(key, 3, 60000));
		assertTrue(slidingWindowRateLimiter.isRateLimited(key, 3, 60000));

		slidingWindowRateLimiter.reset(key);
		assertFalse(slidingWindowRateLimiter.isRateLimited(key, 3, 60000));
	}
}
