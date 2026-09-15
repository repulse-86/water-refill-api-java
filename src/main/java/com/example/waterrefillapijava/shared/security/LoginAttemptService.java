package com.example.waterrefillapijava.shared.security;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.waterrefillapijava.shared.exception.RateLimitedException;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class LoginAttemptService {

	@Value("${app.security.lockout.max-failures:10}")
	private int maxFailures;

	@Value("${app.security.lockout.duration-ms:900000}")
	private long lockoutDurationMs;

	private final ConcurrentHashMap<String, AttemptEntry> attempts = new ConcurrentHashMap<>();

	public void checkAndThrow(String username) {
		final AttemptEntry entry = attempts.get(username);
		if (entry != null && entry.isLockedOut(maxFailures, lockoutDurationMs)) {
			log.warn("Account locked out: username={}", username);
			throw new RateLimitedException("Account is temporarily locked due to too many failed login attempts. Please try again later.");
		}
	}

	public void recordFailure(String username) {
		attempts.compute(username, (key, existing) -> {
			if (existing == null || existing.isExpired(lockoutDurationMs)) {
				return new AttemptEntry(1, System.currentTimeMillis());
			}
			return new AttemptEntry(existing.failures + 1, existing.firstFailureAt);
		});
	}

	public void reset(String username) {
		attempts.remove(username);
	}

	private static class AttemptEntry {
		final int failures;
		final long firstFailureAt;

		AttemptEntry(int failures, long firstFailureAt) {
			this.failures = failures;
			this.firstFailureAt = firstFailureAt;
		}

		boolean isLockedOut(int maxFailures, long durationMs) {
			return failures >= maxFailures && !isExpired(durationMs);
		}

		boolean isExpired(long durationMs) {
			return (System.currentTimeMillis() - firstFailureAt) >= durationMs;
		}
	}
}
