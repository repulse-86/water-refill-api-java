package com.example.waterrefillapijava.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waterrefillapijava.exception.AuthenticationException;
import com.example.waterrefillapijava.exception.RateLimitedException;
import com.example.waterrefillapijava.model.RefreshToken;
import com.example.waterrefillapijava.model.User;
import com.example.waterrefillapijava.repository.RefreshTokenRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

	@Value("${app.refresh.session-expiration-ms:86400000}")
	private long sessionExpirationMs;

	@Value("${app.jwt.remember-expiration-ms:604800000}")
	private long rememberExpirationMs;

	@Value("${app.jwt.secret:demo-jwt-secret-change-me-in-production}")
	private String jwtSecret;

	@Value("${app.trust-proxy:false}")
	private boolean trustProxy;

	@Value("${app.refresh.fingerprint-max-mismatches:5}")
	private int maxFingerprintMismatches;

	@Value("${app.refresh.fingerprint-window-ms:600000}")
	private long fingerprintWindowMs;

	private final RefreshTokenRepository refreshTokenRepository;
	private final JwtUtil jwtUtil;
	private final SlidingWindowRateLimiter slidingWindowRateLimiter;

	@Transactional
	public TokenWithFamily generateRefreshTokenWithFamily(
		@NonNull final User user,
		final boolean remember,
		@NonNull final HttpServletRequest request
	) {
		return generateRefreshTokenWithFamily(user, UUID.randomUUID().toString(), remember, request);
	}

	@Transactional
	public TokenWithFamily generateRefreshTokenWithFamily(
		@NonNull final User user,
		@NonNull final String family,
		final boolean remember,
		@NonNull final HttpServletRequest request
	) {
		final long duration = remember ? rememberExpirationMs : sessionExpirationMs;
		final String rawToken = jwtUtil.generateRefreshToken(user.getUsername(), duration, remember);
		final Instant expiresAt = Instant.now().plusMillis(duration);

		final String fingerprint = ClientFingerprintUtil.computeFingerprint(request, jwtSecret, trustProxy);

		final RefreshToken entity = new RefreshToken();
		entity.setTokenHash(hashToken(rawToken));
		entity.setTokenFamily(family);
		entity.setUserId(user.getId());
		entity.setExpiresAt(expiresAt);
		entity.setRevoked(false);
		entity.setFingerprint(fingerprint);

		refreshTokenRepository.save(entity);

		return new TokenWithFamily(rawToken, family, expiresAt);
	}

	@Transactional
	public TokenWithFamily verifyAndRotateRefreshToken(
		@NonNull final String rawToken,
		@NonNull final User user,
		@NonNull final HttpServletRequest request
	) {
		if (!jwtUtil.validateToken(rawToken)) {
			throw new AuthenticationException("Invalid or expired refresh token");
		}

		final String tokenHash = hashToken(rawToken);
		final RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
			.orElseThrow(() -> new AuthenticationException("Refresh token not found"));

		if (stored.isRevoked()) {
			refreshTokenRepository.revokeActiveByTokenFamily(stored.getTokenFamily());
			log.warn("Security Alert: Reuse of revoked refresh token detected for family={}", stored.getTokenFamily());
			throw new AuthenticationException("Refresh token has been revoked. All sessions invalidated.");
		}

		if (stored.getExpiresAt().isBefore(Instant.now())) {
			throw new AuthenticationException("Refresh token has expired");
		}

		final String clientIp = ClientFingerprintUtil.extractClientIp(request, trustProxy);
		final String fingerprint = stored.getFingerprint();
		if (fingerprint == null) {
			log.debug("Refresh token without fingerprint (legacy) accepted for family={}", stored.getTokenFamily());
		} else if (!ClientFingerprintUtil.constantTimeEquals(fingerprint,
			ClientFingerprintUtil.computeFingerprint(request, jwtSecret, trustProxy))) {
			rejectFingerprintMismatch(clientIp, stored.getTokenFamily(), user.getId());
		}

		slidingWindowRateLimiter.reset(clientIp);

		final String existingFamily = stored.getTokenFamily();
		refreshTokenRepository.revokeActiveByTokenFamily(existingFamily);

		final boolean remember = jwtUtil.isRememberToken(rawToken);
		return generateRefreshTokenWithFamily(user, existingFamily, remember, request);
	}

	private void rejectFingerprintMismatch(
		@NonNull final String clientIp,
		@NonNull final String family,
		@NonNull final Long userId
	) {
		if (slidingWindowRateLimiter.isRateLimited(clientIp, maxFingerprintMismatches, fingerprintWindowMs)) {
			log.warn("Refresh rate limit exceeded for ip={}", clientIp);
			throw new RateLimitedException("Too many refresh attempts. Please try again later.");
		}
		refreshTokenRepository.revokeActiveByTokenFamily(family);
		log.warn("Security Alert: Refresh token binding mismatch - potential token theft. userId={}, family={}, ip={}",
			userId, family, clientIp);
		throw new AuthenticationException("Security violation. Device binding mismatch. Please log in again.");
	}

	@Transactional
	public void revokeAllUserTokens(@NonNull final Long userId) {
		refreshTokenRepository.revokeAllActiveByUserId(userId);
	}

	@Transactional
	public void revokeTokenFamilyByRawToken(final String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return;
		}
		try {
			if (!jwtUtil.validateToken(rawToken)) {
				return;
			}
			final String tokenHash = hashToken(rawToken);
			refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(stored -> {
				refreshTokenRepository.revokeByTokenFamily(stored.getTokenFamily());
				log.info("Revoked token family {} on logout", stored.getTokenFamily());
			});
		} catch (final Exception e) {
			log.warn("Failed to revoke token family on logout: {}", e.getMessage());
		}
	}

	public String hashToken(@NonNull final String rawToken) {
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			final byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (final NoSuchAlgorithmException e) {
			throw new RuntimeException("Error hashing token", e);
		}
	}

	@Transactional
	public int purgeExpiredTokens() {
		final int deleted = refreshTokenRepository.deleteExpiredBefore(Instant.now());
		log.info("Purged {} expired refresh tokens", deleted);
		return deleted;
	}

	public record TokenWithFamily(String token, String family, Instant expiresAt) {
	}
}
