package com.example.waterrefillapijava.config;

import java.time.Duration;
import java.util.Set;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
public class CacheConfig {

	@Bean
	public CacheManager cacheManager() {
		final CaffeineCacheManager manager = new CaffeineCacheManager();
		manager.setCacheNames(Set.of(
			"settings",
			"dashboard",
			"report:daily-sales",
			"report:product-performance",
			"report:debt-aging",
			"report:reconciliation"
		));
		manager.setCaffeine(Caffeine.newBuilder()
			.maximumSize(100)
			.expireAfterWrite(Duration.ofMinutes(5)));
		manager.setAllowNullValues(false);
		return manager;
	}
}
