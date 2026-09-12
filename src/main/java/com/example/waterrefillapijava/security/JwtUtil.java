package com.example.waterrefillapijava.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;

@Component
public class JwtUtil {

	@Value("${app.jwt.secret:demo-jwt-secret-change-me-in-production}")
	private String secret;

	@Value("${app.jwt.access-expiration-ms:900000}")
	private long accessTokenExpirationMs;

	@Value("${app.jwt.issuer:water-refill}")
	private String issuer;

	@Value("${app.jwt.audience:water-refill-api}")
	private String audience;

	private SecretKey key;

	@PostConstruct
	public void init() {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	public String generateAccessToken(@NonNull final String username, final boolean remember) {
		return generateToken(username, accessTokenExpirationMs, remember);
	}

	public String generateRefreshToken(@NonNull final String username, final long durationMs, final boolean remember) {
		return generateToken(username, durationMs, remember);
	}

	private String generateToken(@NonNull final String username, final long expirationMs, final boolean remember) {
		final Date now = new Date();
		final Date expiryDate = new Date(now.getTime() + expirationMs);

		return Jwts.builder()
			.id(UUID.randomUUID().toString())
			.subject(username)
			.issuedAt(now)
			.expiration(expiryDate)
			.issuer(issuer)
			.audience().add(audience).and()
			.claim("remember", remember)
			.signWith(key)
			.compact();
	}

	public String getUserFromToken(@NonNull final String token) {
		return parseValidatedClaims(token).getSubject();
	}

	public boolean isRememberToken(@NonNull final String token) {
		try {
			final Claims claims = parseValidatedClaims(token);
			final Boolean remember = claims.get("remember", Boolean.class);
			return Boolean.TRUE.equals(remember);
		} catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}

	public boolean validateToken(@NonNull final String token) {
		try {
			parseValidatedClaims(token);
			return true;
		} catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}

	private Claims parseValidatedClaims(@NonNull final String token) {
		return Jwts.parser()
			.verifyWith(key)
			.requireIssuer(issuer)
			.requireAudience(audience)
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}
}
