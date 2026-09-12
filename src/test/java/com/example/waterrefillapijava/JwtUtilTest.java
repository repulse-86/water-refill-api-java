package com.example.waterrefillapijava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.waterrefillapijava.security.JwtUtil;

import io.jsonwebtoken.JwtException;

class JwtUtilTest {

	private static final String SECRET = "test-secret-minimum-32-characters-long";
	private static final String ISSUER = "water-refill";
	private static final String AUDIENCE = "water-refill-api";
	private static final long ACCESS_EXPIRATION_MS = 900000;

	private JwtUtil jwtUtil;

	@BeforeEach
	void setUp() {
		jwtUtil = new JwtUtil();
		ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
		ReflectionTestUtils.setField(jwtUtil, "accessTokenExpirationMs", ACCESS_EXPIRATION_MS);
		ReflectionTestUtils.setField(jwtUtil, "issuer", ISSUER);
		ReflectionTestUtils.setField(jwtUtil, "audience", AUDIENCE);
		jwtUtil.init();
	}

	@Test
	void generateAccessTokenProducesValidToken() {
		final String token = jwtUtil.generateAccessToken("admin", false);

		assertNotNull(token);
		assertTrue(token.split("\\.").length == 3);
		assertEquals("admin", jwtUtil.getUserFromToken(token));
		assertTrue(jwtUtil.validateToken(token));
	}

	@Test
	void accessTokenWithRememberFlag() {
		final String token = jwtUtil.generateAccessToken("admin", true);

		assertTrue(jwtUtil.isRememberToken(token));
		assertTrue(jwtUtil.validateToken(token));
	}

	@Test
	void accessTokenWithoutRememberFlag() {
		final String token = jwtUtil.generateAccessToken("admin", false);

		assertFalse(jwtUtil.isRememberToken(token));
		assertTrue(jwtUtil.validateToken(token));
	}

	@Test
	void generateRefreshTokenWithCustomDuration() {
		final long durationMs = 604800000L;
		final String token = jwtUtil.generateRefreshToken("admin", durationMs, true);

		assertNotNull(token);
		assertEquals("admin", jwtUtil.getUserFromToken(token));
		assertTrue(jwtUtil.isRememberToken(token));
		assertTrue(jwtUtil.validateToken(token));
	}

	@Test
	void tamperedTokenFailsValidation() {
		final String token = jwtUtil.generateAccessToken("admin", false);
		final String tampered = token.substring(0, token.length() - 5) + "XXXXX";

		assertFalse(jwtUtil.validateToken(tampered));
	}

	@Test
	void wrongSecretRejectsToken() {
		final JwtUtil other = new JwtUtil();
		ReflectionTestUtils.setField(other, "secret", "different-secret-at-least-32-chars");
		ReflectionTestUtils.setField(other, "accessTokenExpirationMs", ACCESS_EXPIRATION_MS);
		ReflectionTestUtils.setField(other, "issuer", ISSUER);
		ReflectionTestUtils.setField(other, "audience", AUDIENCE);
		other.init();

		final String token = jwtUtil.generateAccessToken("admin", false);

		assertThrows(JwtException.class, () -> other.getUserFromToken(token));
		assertFalse(other.validateToken(token));
	}

	@Test
	void wrongIssuerRejectsToken() {
		final JwtUtil other = new JwtUtil();
		ReflectionTestUtils.setField(other, "secret", SECRET);
		ReflectionTestUtils.setField(other, "accessTokenExpirationMs", ACCESS_EXPIRATION_MS);
		ReflectionTestUtils.setField(other, "issuer", "wrong-issuer");
		ReflectionTestUtils.setField(other, "audience", AUDIENCE);
		other.init();

		final String token = jwtUtil.generateAccessToken("admin", false);

		assertThrows(JwtException.class, () -> other.getUserFromToken(token));
		assertFalse(other.validateToken(token));
	}

	@Test
	void wrongAudienceRejectsToken() {
		final JwtUtil other = new JwtUtil();
		ReflectionTestUtils.setField(other, "secret", SECRET);
		ReflectionTestUtils.setField(other, "accessTokenExpirationMs", ACCESS_EXPIRATION_MS);
		ReflectionTestUtils.setField(other, "issuer", ISSUER);
		ReflectionTestUtils.setField(other, "audience", "wrong-audience");
		other.init();

		final String token = jwtUtil.generateAccessToken("admin", false);

		assertThrows(JwtException.class, () -> other.getUserFromToken(token));
		assertFalse(other.validateToken(token));
	}

	@Test
	void isRememberTokenReturnsFalseForMalformedToken() {
		assertFalse(jwtUtil.isRememberToken("not-a-valid-jwt"));
	}

	@Test
	void validateTokenReturnsFalseForMalformedToken() {
		assertFalse(jwtUtil.validateToken("not-a-valid-jwt"));
	}
}
