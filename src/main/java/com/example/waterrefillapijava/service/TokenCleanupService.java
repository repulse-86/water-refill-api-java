package com.example.waterrefillapijava.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waterrefillapijava.security.TokenService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupService {

	private final TokenService tokenService;

	@Scheduled(cron = "${app.jwt-purge-cron:0 0 3 * * ?}")
	@Transactional
	public void purgeExpiredTokens() {
		final int deleted = tokenService.purgeExpiredTokens();
		log.info("Scheduled purge removed {} expired refresh tokens", deleted);
	}
}
