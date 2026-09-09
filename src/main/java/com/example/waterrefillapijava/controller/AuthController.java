package com.example.waterrefillapijava.controller;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.waterrefillapijava.dto.ErrorResponse;
import com.example.waterrefillapijava.dto.LoginRequest;
import com.example.waterrefillapijava.dto.LoginResponse;
import com.example.waterrefillapijava.dto.MessageResponse;
import com.example.waterrefillapijava.model.User;
import com.example.waterrefillapijava.security.CookieUtil;
import com.example.waterrefillapijava.security.JwtUtil;
import com.example.waterrefillapijava.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

	private final UserService userService;
	private final PasswordEncoder passwordEncoder;
	private final JwtUtil jwtUtil;
	private final CookieUtil cookieUtil;

	@Value("${app.jwt.access-expiration-ms:900000}")
	private long accessTokenExpirationMs;

	@Value("${app.jwt.remember-expiration-ms:604800000}")
	private long rememberTokenExpirationMs;

	@PostMapping("/login")
	@Transactional
	public ResponseEntity<?> login(
		@Valid @RequestBody final LoginRequest request,
		final HttpServletRequest httpRequest,
		final HttpServletResponse response
	) {
		final Optional<User> userOpt = userService.loadByUsername(request.username());

		if (userOpt.isEmpty() || !passwordEncoder.matches(request.password(), userOpt.get().getPassword())) {
			log.warn("Login failed: username={}", request.username());
			return ResponseEntity.status(401).body(
				ErrorResponse.of("Your credentials do not exist in our records.")
			);
		}

		final User user = userOpt.get();

		final String accessToken = jwtUtil.generateAccessToken(user.getUsername(), request.remember());
		final String refreshToken = jwtUtil.generateRefreshToken(user.getUsername(), request.remember());

		final long accessDurationMs = request.remember() ? rememberTokenExpirationMs : accessTokenExpirationMs;
		final long refreshDurationMs = rememberTokenExpirationMs;

		cookieUtil.addTokenCookies(response, accessToken, refreshToken, accessDurationMs, refreshDurationMs, request.remember());

		log.info("Login successful: userId={}, username={}", user.getId(), user.getUsername());

		final LoginResponse body = LoginResponse.of(accessToken, user.getId(), user.getUsername());

		return ResponseEntity.ok(body);
	}

	@PostMapping("/logout")
	@Transactional
	public ResponseEntity<?> logout(final HttpServletResponse response) {
		final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof User user) {
			log.info("Logout: userId={}, username={}", user.getId(), user.getUsername());
		}

		SecurityContextHolder.clearContext();
		cookieUtil.clearTokenCookies(response);

		return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
	}

	@PostMapping("/refresh")
	public ResponseEntity<?> refresh(
		final HttpServletRequest httpRequest,
		final HttpServletResponse response
	) {
		final String refreshToken = cookieUtil.extractCookie(httpRequest, "refresh_token").orElse(null);

		if (refreshToken == null || refreshToken.isBlank() || !jwtUtil.validateToken(refreshToken)) {
			return ResponseEntity.status(401).body(
				ErrorResponse.of("Invalid or expired refresh token")
			);
		}

		final String username = jwtUtil.extractSubject(refreshToken);
		final boolean remember = jwtUtil.isRememberToken(refreshToken);

		final String newAccessToken = jwtUtil.generateAccessToken(username, remember);
		final String newRefreshToken = jwtUtil.generateRefreshToken(username, remember);

		final long accessDurationMs = remember ? rememberTokenExpirationMs : accessTokenExpirationMs;

		cookieUtil.addTokenCookies(response, newAccessToken, newRefreshToken, accessDurationMs, rememberTokenExpirationMs, remember);
		log.info("Token refreshed: username={}", username);

		return ResponseEntity.ok(Map.of("token", newAccessToken));
	}

	@GetMapping("/me")
	public ResponseEntity<?> me() {
		final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User user)) {
			return ResponseEntity.status(401).body(ErrorResponse.of("Not authenticated"));
		}

		final LoginResponse body = new LoginResponse(
			null,
			Map.of("id", user.getId(), "username", user.getUsername())
		);

		return ResponseEntity.ok(body);
	}

	@GetMapping("/ping")
	public ResponseEntity<?> ping() {
		return ResponseEntity.ok(Map.of("status", "ok", "timestamp", Instant.now().toString()));
	}
}
