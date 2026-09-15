package com.example.waterrefillapijava.shared.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SlidingWindowRateLimiter {

	private final ConcurrentHashMap<String, WindowEntry> windows = new ConcurrentHashMap<>();

	public boolean isRateLimited(final String key, final int maxAttempts, final long windowMs) {
		final long now = System.currentTimeMillis();
		final boolean[] limited = new boolean[1];
		windows.compute(key, (k, entry) -> {
			if (entry == null || now - entry.windowStart >= windowMs) {
				entry = new WindowEntry(now, new AtomicLong(0));
			}
			final long current = entry.count.incrementAndGet();
			limited[0] = current > maxAttempts;
			return entry;
		});
		return limited[0];
	}

	public void reset(final String key) {
		windows.remove(key);
	}

	private static class WindowEntry {
		private final long windowStart;
		private final AtomicLong count;

		private WindowEntry(final long windowStart, final AtomicLong count) {
			this.windowStart = windowStart;
			this.count = count;
		}
	}
}
