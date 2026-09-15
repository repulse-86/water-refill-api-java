package com.example.waterrefillapijava.shared.security;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;

@Component
public class CookieUtil {

	@Value("${app.cookie.secure:false}")
	private boolean secure;

	@Value("${app.cookie.samesite:Lax}")
	private String sameSite;

	@Value("${app.cookie.refresh-samesite:Strict}")
	private String refreshSameSite;

	public void addTokenCookies(
		@NonNull final HttpServletResponse response,
		@NonNull final String accessToken,
		@NonNull final String refreshToken,
		final long refreshDurationMs,
		final long accessTokenDurationMs,
		final boolean remember
	) {
		final ResponseCookie accessCookie = ResponseCookie.from("access_token", accessToken)
			.httpOnly(true)
			.secure(secure)
			.path("/")
			.maxAge(Duration.ofMillis(accessTokenDurationMs))
			.sameSite(sameSite)
			.build();

		final ResponseCookie.ResponseCookieBuilder refreshBuilder = ResponseCookie.from("refresh_token", refreshToken)
			.httpOnly(true)
			.secure(secure)
			.path("/api/v1/refresh")
			.sameSite(refreshSameSite);
		if (remember) {
			refreshBuilder.maxAge(refreshDurationMs / 1000);
		}
		final ResponseCookie refreshCookie = refreshBuilder.build();

		response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
		response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
	}

	public void clearTokenCookies(@NonNull final HttpServletResponse response) {
		final ResponseCookie accessCookie = ResponseCookie.from("access_token", "")
			.httpOnly(true)
			.secure(secure)
			.path("/")
			.maxAge(0)
			.sameSite(sameSite)
			.build();

		final ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", "")
			.httpOnly(true)
			.secure(secure)
			.path("/api/v1/refresh")
			.maxAge(0)
			.sameSite(refreshSameSite)
			.build();

		response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
		response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
	}

	public Optional<String> extractCookie(@NonNull final HttpServletRequest request, @NonNull final String cookieName) {
		if (request.getCookies() == null) {
			return Optional.empty();
		}

		return Arrays.stream(request.getCookies())
			.filter(cookie -> cookieName.equals(cookie.getName()))
			.map(Cookie::getValue)
			.findFirst();
	}
}
