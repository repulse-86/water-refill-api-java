package com.example.waterrefillapijava.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.waterrefillapijava.shared.exception.AuthenticationException;
import com.example.waterrefillapijava.shared.exception.RateLimitedException;
import com.example.waterrefillapijava.shared.model.RefreshToken;
import com.example.waterrefillapijava.shared.model.User;
import com.example.waterrefillapijava.shared.repository.RefreshTokenRepository;
import com.example.waterrefillapijava.shared.security.ClientFingerprintUtil;
import com.example.waterrefillapijava.shared.security.JwtUtil;
import com.example.waterrefillapijava.shared.security.SlidingWindowRateLimiter;
import com.example.waterrefillapijava.shared.security.TokenService;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

	private static final String SECRET = "test-secret-minimum-32-characters-long";

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@Mock
	private JwtUtil jwtUtil;

	@Mock
	private SlidingWindowRateLimiter slidingWindowRateLimiter;

	@InjectMocks
	private TokenService tokenService;

	private MockHttpServletRequest request;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(tokenService, "sessionExpirationMs", 86400000L);
		ReflectionTestUtils.setField(tokenService, "rememberExpirationMs", 604800000L);
		ReflectionTestUtils.setField(tokenService, "jwtSecret", SECRET);
		ReflectionTestUtils.setField(tokenService, "trustProxy", false);
		ReflectionTestUtils.setField(tokenService, "maxFingerprintMismatches", 5);
		ReflectionTestUtils.setField(tokenService, "fingerprintWindowMs", 600000L);

		request = new MockHttpServletRequest();
		request.setRemoteAddr("127.0.0.1");
	}

	private User createUser() {
		final User user = new User();
		user.setId(1L);
		user.setUsername("admin");
		return user;
	}

	@Test
	void hashTokenProducesDeterministicSha256() {
		final String hash1 = tokenService.hashToken("raw-token");
		final String hash2 = tokenService.hashToken("raw-token");

		assertEquals(hash1, hash2);
		assertEquals(64, hash1.length());
	}

	@Test
	void hashTokenProducesDifferentHashes() {
		final String hash1 = tokenService.hashToken("token-a");
		final String hash2 = tokenService.hashToken("token-b");

		assertFalse(hash1.equals(hash2));
	}

	@Test
	void generateRefreshTokenWithFamilySavesEntity() {
		final User user = createUser();
		when(jwtUtil.generateRefreshToken(eq("admin"), eq(86400000L), eq(false)))
			.thenReturn("raw-refresh-token");
		when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

		final TokenService.TokenWithFamily result =
			tokenService.generateRefreshTokenWithFamily(user, false, request);

		assertNotNull(result);
		assertEquals("raw-refresh-token", result.token());
		assertNotNull(result.family());
		assertNotNull(result.expiresAt());

		final ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository).save(captor.capture());
		final RefreshToken saved = captor.getValue();
		assertNotNull(saved.getTokenHash());
		assertNotNull(saved.getTokenFamily());
		assertEquals(1L, saved.getUserId());
		assertFalse(saved.isRevoked());
		assertNotNull(saved.getFingerprint());
	}

	@Test
	void generateRefreshTokenWithRememberUsesLongerDuration() {
		final User user = createUser();
		when(jwtUtil.generateRefreshToken(eq("admin"), eq(604800000L), eq(true)))
			.thenReturn("raw-remember-token");
		when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

		final TokenService.TokenWithFamily result =
			tokenService.generateRefreshTokenWithFamily(user, true, request);

		assertEquals("raw-remember-token", result.token());
		verify(jwtUtil).generateRefreshToken("admin", 604800000L, true);
	}

	@Test
	void verifyAndRotateSuccess() {
		final User user = createUser();
		final RefreshToken stored = new RefreshToken();
		stored.setTokenHash("existing-hash");
		stored.setTokenFamily("family-1");
		stored.setUserId(1L);
		stored.setExpiresAt(Instant.now().plusSeconds(3600));
		stored.setRevoked(false);
		stored.setFingerprint(ClientFingerprintUtil.computeFingerprint(request, SECRET, false));

		when(jwtUtil.validateToken("raw-token")).thenReturn(true);
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
		lenient().when(slidingWindowRateLimiter.isRateLimited(anyString(), anyInt(), anyLong())).thenReturn(false);
		when(jwtUtil.isRememberToken("raw-token")).thenReturn(false);
		when(jwtUtil.generateRefreshToken(eq("admin"), eq(86400000L), eq(false)))
			.thenReturn("new-raw-token");
		when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

		final TokenService.TokenWithFamily result =
			tokenService.verifyAndRotateRefreshToken("raw-token", user, request);

		assertNotNull(result);
		assertEquals("new-raw-token", result.token());
		assertEquals("family-1", result.family());
		verify(refreshTokenRepository).revokeActiveByTokenFamily("family-1");
		verify(slidingWindowRateLimiter).reset(anyString());
	}

	@Test
	void verifyAndRotateInvalidTokenThrows() {
		final User user = createUser();
		when(jwtUtil.validateToken("bad-token")).thenReturn(false);

		assertThrows(AuthenticationException.class,
			() -> tokenService.verifyAndRotateRefreshToken("bad-token", user, request));
	}

	@Test
	void verifyAndRotateTokenNotFoundThrows() {
		final User user = createUser();
		when(jwtUtil.validateToken("raw-token")).thenReturn(true);
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

		assertThrows(AuthenticationException.class,
			() -> tokenService.verifyAndRotateRefreshToken("raw-token", user, request));
	}

	@Test
	void verifyAndRotateRevokedTokenRevokesFamilyAndThrows() {
		final User user = createUser();
		final RefreshToken stored = new RefreshToken();
		stored.setTokenHash("existing-hash");
		stored.setTokenFamily("family-1");
		stored.setUserId(1L);
		stored.setExpiresAt(Instant.now().plusSeconds(3600));
		stored.setRevoked(true);
		stored.setFingerprint("fingerprint");

		when(jwtUtil.validateToken("raw-token")).thenReturn(true);
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

		assertThrows(AuthenticationException.class,
			() -> tokenService.verifyAndRotateRefreshToken("raw-token", user, request));
		verify(refreshTokenRepository).revokeActiveByTokenFamily("family-1");
	}

	@Test
	void verifyAndRotateExpiredTokenThrows() {
		final User user = createUser();
		final RefreshToken stored = new RefreshToken();
		stored.setTokenHash("existing-hash");
		stored.setTokenFamily("family-1");
		stored.setUserId(1L);
		stored.setExpiresAt(Instant.now().minusSeconds(3600));
		stored.setRevoked(false);
		stored.setFingerprint(ClientFingerprintUtil.computeFingerprint(request, SECRET, false));

		when(jwtUtil.validateToken("raw-token")).thenReturn(true);
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

		assertThrows(AuthenticationException.class,
			() -> tokenService.verifyAndRotateRefreshToken("raw-token", user, request));
	}

	@Test
	void verifyAndRotateFingerprintMismatchRevokesFamilyAndThrows() {
		final User user = createUser();
		final RefreshToken stored = new RefreshToken();
		stored.setTokenHash("existing-hash");
		stored.setTokenFamily("family-1");
		stored.setUserId(1L);
		stored.setExpiresAt(Instant.now().plusSeconds(3600));
		stored.setRevoked(false);
		stored.setFingerprint("different-fingerprint");

		when(jwtUtil.validateToken("raw-token")).thenReturn(true);
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
		when(slidingWindowRateLimiter.isRateLimited(anyString(), anyInt(), anyLong())).thenReturn(false);

		assertThrows(AuthenticationException.class,
			() -> tokenService.verifyAndRotateRefreshToken("raw-token", user, request));
		verify(refreshTokenRepository).revokeActiveByTokenFamily("family-1");
	}

	@Test
	void verifyAndRotateFingerprintMismatchRateLimitedThrowsRateLimited() {
		final User user = createUser();
		final RefreshToken stored = new RefreshToken();
		stored.setTokenHash("existing-hash");
		stored.setTokenFamily("family-1");
		stored.setUserId(1L);
		stored.setExpiresAt(Instant.now().plusSeconds(3600));
		stored.setRevoked(false);
		stored.setFingerprint("different-fingerprint");

		when(jwtUtil.validateToken("raw-token")).thenReturn(true);
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
		when(slidingWindowRateLimiter.isRateLimited(anyString(), anyInt(), anyLong())).thenReturn(true);

		assertThrows(RateLimitedException.class,
			() -> tokenService.verifyAndRotateRefreshToken("raw-token", user, request));
	}

	@Test
	void revokeAllUserTokens() {
		tokenService.revokeAllUserTokens(1L);
		verify(refreshTokenRepository).revokeAllActiveByUserId(1L);
	}

	@Test
	void revokeTokenFamilyByRawTokenSuccess() {
		final RefreshToken stored = new RefreshToken();
		stored.setTokenFamily("family-1");
		when(jwtUtil.validateToken("raw-token")).thenReturn(true);
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

		tokenService.revokeTokenFamilyByRawToken("raw-token");
		verify(refreshTokenRepository).revokeByTokenFamily("family-1");
	}

	@Test
	void revokeTokenFamilyByRawTokenNullReturnsSilently() {
		tokenService.revokeTokenFamilyByRawToken(null);
		verify(refreshTokenRepository, never()).findByTokenHash(anyString());
	}

	@Test
	void purgeExpiredTokensReturnsCount() {
		when(refreshTokenRepository.deleteExpiredBefore(any(Instant.class))).thenReturn(5);
		final int result = tokenService.purgeExpiredTokens();
		assertEquals(5, result);
	}
}
