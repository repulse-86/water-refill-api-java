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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.waterrefillapijava.dto.LoginRequest;
import com.example.waterrefillapijava.dto.LoginResponse;
import com.example.waterrefillapijava.dto.MessageResponse;
import com.example.waterrefillapijava.dto.RefreshTokenRequest;
import com.example.waterrefillapijava.dto.UpdatePasswordRequest;
import com.example.waterrefillapijava.dto.UpdateProfileRequest;
import com.example.waterrefillapijava.exception.AuthenticationException;
import com.example.waterrefillapijava.exception.ConflictException;
import com.example.waterrefillapijava.exception.FieldValidationException;
import com.example.waterrefillapijava.model.User;
import com.example.waterrefillapijava.security.ApiRateLimiter;
import com.example.waterrefillapijava.security.ClientFingerprintUtil;
import com.example.waterrefillapijava.security.CookieUtil;
import com.example.waterrefillapijava.security.JwtUtil;
import com.example.waterrefillapijava.security.TokenService;
import com.example.waterrefillapijava.security.TokenService.TokenWithFamily;
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
	private final TokenService tokenService;
	private final ApiRateLimiter apiRateLimiter;
	private final com.example.waterrefillapijava.security.LoginAttemptService loginAttemptService;

	@Value("${app.jwt.access-expiration-ms:900000}")
	private long accessTokenExpirationMs;

	@Value("${app.jwt.remember-expiration-ms:604800000}")
	private long rememberTokenExpirationMs;

	@Value("${app.trust-proxy:false}")
	private boolean trustProxy;

	@PostMapping("/login")
	@Transactional
	public ResponseEntity<?> login(
		@Valid @RequestBody final LoginRequest request,
		final HttpServletRequest httpRequest,
		final HttpServletResponse response
	) {
		final String clientIp = ClientFingerprintUtil.extractClientIp(httpRequest, trustProxy);
		apiRateLimiter.checkLogin(clientIp, request.username());
		loginAttemptService.checkAndThrow(request.username());

		final Optional<User> userOpt = userService.loadByUsername(request.username());

		if (userOpt.isEmpty() || !passwordEncoder.matches(request.password(), userOpt.get().getPassword())) {
			loginAttemptService.recordFailure(request.username());
			log.warn("Login failed: username={}", request.username());
			throw new AuthenticationException("Your credentials do not exist in our records.");
		}

		final User user = userOpt.get();
		apiRateLimiter.resetLogin(clientIp, user.getUsername());
		loginAttemptService.reset(user.getUsername());

		final String accessToken = jwtUtil.generateAccessToken(user.getUsername(), request.remember());
		final TokenWithFamily refresh = tokenService.generateRefreshTokenWithFamily(user, request.remember(), httpRequest);

		final long accessDurationMs = request.remember() ? rememberTokenExpirationMs : accessTokenExpirationMs;
		final long refreshDurationMs = refresh.expiresAt().toEpochMilli() - Instant.now().toEpochMilli();

		cookieUtil.addTokenCookies(response, accessToken, refresh.token(), accessDurationMs, refreshDurationMs, request.remember());

		log.info("Login successful: userId={}, username={}, ip={}", user.getId(), user.getUsername(), clientIp);

		final LoginResponse body = LoginResponse.of(accessToken, user.getId(), user.getUsername());

		return ResponseEntity.ok(body);
	}

	@PostMapping("/refresh")
	@Transactional
	public ResponseEntity<?> refresh(
		@CookieValue(name = "refresh_token", required = false) String refreshToken,
		@RequestBody(required = false) final RefreshTokenRequest request,
		final HttpServletRequest httpRequest,
		final HttpServletResponse response
	) {
		final String clientIp = ClientFingerprintUtil.extractClientIp(httpRequest, trustProxy);
		apiRateLimiter.checkRefresh(clientIp);

		if (refreshToken == null && request != null) {
			refreshToken = request.refreshToken();
		}

		if (refreshToken == null || refreshToken.isBlank()) {
			throw new AuthenticationException("Missing refresh token");
		}

		if (!jwtUtil.validateToken(refreshToken)) {
			throw new AuthenticationException("Invalid refresh token");
		}

		final String username = jwtUtil.extractSubject(refreshToken);
		final Optional<User> userOpt = userService.loadByUsername(username);

		if (userOpt.isEmpty()) {
			throw new AuthenticationException("User not found");
		}

		final User user = userOpt.get();
		final TokenWithFamily rotated = tokenService.verifyAndRotateRefreshToken(refreshToken, user, httpRequest);

		final String newAccessToken = jwtUtil.generateAccessToken(user.getUsername(), false);
		final long refreshDurationMs = rotated.expiresAt().toEpochMilli() - Instant.now().toEpochMilli();

		cookieUtil.addTokenCookies(response, newAccessToken, rotated.token(), accessTokenExpirationMs, refreshDurationMs,
			jwtUtil.isRememberToken(refreshToken));

		log.info("Token refreshed: userId={}", user.getId());

		final LoginResponse body = LoginResponse.of(newAccessToken, user.getId(), user.getUsername());

		return ResponseEntity.ok(body);
	}

	@PostMapping("/logout")
	@Transactional
	public ResponseEntity<?> logout(
		@CookieValue(name = "refresh_token", required = false) final String refreshToken,
		final HttpServletResponse response
	) {
		final Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		if (auth != null && auth.getPrincipal() instanceof User user) {
			tokenService.revokeAllUserTokens(user.getId());
			log.info("Logout: userId={}, username={}", user.getId(), user.getUsername());
		}

		if (refreshToken != null && !refreshToken.isBlank()) {
			tokenService.revokeTokenFamilyByRawToken(refreshToken);
		}

		SecurityContextHolder.clearContext();
		cookieUtil.clearTokenCookies(response);

		return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
	}

	@GetMapping("/me")
	public ResponseEntity<?> me() {
		final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User user)) {
			throw new AuthenticationException("Not authenticated");
		}

		final LoginResponse body = new LoginResponse(
			null,
			Map.of("id", user.getId(), "username", user.getUsername())
		);

		return ResponseEntity.ok(body);
	}

	@PutMapping("/user/profile-information")
	@Transactional
	public ResponseEntity<?> updateProfile(@Valid @RequestBody final UpdateProfileRequest request) {
		final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User user)) {
			throw new AuthenticationException("Not authenticated");
		}

		if (!user.getUsername().equalsIgnoreCase(request.username()) && userService.existsByUsernameAndIdNot(request.username(), user.getId())) {
			throw FieldValidationException.builder()
				.add("username", "The username has already been taken.")
				.build();
		}

		user.setUsername(request.username());
		userService.save(user);

		log.info("Profile updated: userId={}, username={}", user.getId(), user.getUsername());

		final LoginResponse body = new LoginResponse(
			null,
			Map.of("id", user.getId(), "username", user.getUsername())
		);

		return ResponseEntity.ok(Map.of("user", body.user()));
	}

	@PutMapping("/user/password")
	@Transactional
	public ResponseEntity<?> updatePassword(@Valid @RequestBody final UpdatePasswordRequest request) {
		final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User user)) {
			throw new AuthenticationException("Not authenticated");
		}

		FieldValidationException.Builder validator = FieldValidationException.builder();
		boolean hasErrors = false;

		if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
			validator.add("current_password", "The provided current password is incorrect.");
			hasErrors = true;
		}

		if (!request.password().equals(request.passwordConfirmation())) {
			validator.add("password", "Password confirmation does not match.");
			hasErrors = true;
		}

		if (hasErrors) {
			throw validator.build();
		}

		user.setPassword(passwordEncoder.encode(request.password()));
		userService.save(user);

		log.info("Password updated: userId={}", user.getId());

		return ResponseEntity.ok(new MessageResponse("Password updated successfully."));
	}

	@GetMapping("/ping")
	public ResponseEntity<?> ping() {
		return ResponseEntity.ok(Map.of("status", "ok", "timestamp", Instant.now().toString()));
	}
}
