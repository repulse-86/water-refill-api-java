package com.example.waterrefillapijava.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.waterrefillapijava.shared.exception.RateLimitedException;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiRateLimiter {

	private final SlidingWindowRateLimiter slidingWindowRateLimiter;

	@Value("${app.security.rate-limit.login.max:10}")
	private int loginMax;

	@Value("${app.security.rate-limit.login.window-ms:60000}")
	private long loginWindowMs;

	@Value("${app.security.rate-limit.refresh.max:20}")
	private int refreshMax;

	@Value("${app.security.rate-limit.refresh.window-ms:60000}")
	private long refreshWindowMs;

	public void checkLogin(@NonNull final String ip, @NonNull final String username) {
		check("login:" + ip + ":" + username, loginMax, loginWindowMs);
	}

	public void resetLogin(@NonNull final String ip, @NonNull final String username) {
		slidingWindowRateLimiter.reset("login:" + ip + ":" + username);
	}

	public void checkRefresh(@NonNull final String ip) {
		check("refresh:" + ip, refreshMax, refreshWindowMs);
	}

	private void check(@NonNull final String key, final int maxAttempts, final long windowMs) {
		if (slidingWindowRateLimiter.isRateLimited(key, maxAttempts, windowMs)) {
			log.warn("Rate limit exceeded: key={}", key);
			throw new RateLimitedException("Too many requests. Please try again later.");
		}
	}
}
