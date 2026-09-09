package com.example.waterrefillapijava.security;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.waterrefillapijava.model.User;
import com.example.waterrefillapijava.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtUtil jwtUtil;
	private final CookieUtil cookieUtil;
	private final UserRepository userRepository;

	@Override
	protected boolean shouldNotFilter(final HttpServletRequest request) {
		final String path = request.getServletPath();
		return path.startsWith("/ws") || path.equals("/api/v1/login") || path.equals("/api/v1/ping");
	}

	@Override
	protected void doFilterInternal(
		final HttpServletRequest request,
		final HttpServletResponse response,
		final FilterChain filterChain
	) throws ServletException, IOException {

		final String token = extractAccessToken(request);

		if (token != null && jwtUtil.validateToken(token)) {
			final String username = jwtUtil.extractSubject(token);

			if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
				final Optional<User> userOpt = userRepository.findByUsername(username);

				if (userOpt.isPresent()) {
					final User user = userOpt.get();

					final UsernamePasswordAuthenticationToken authentication =
						new UsernamePasswordAuthenticationToken(user, null, java.util.List.of());

					authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

					SecurityContextHolder.getContext().setAuthentication(authentication);
				}
			}
		}

		filterChain.doFilter(request, response);
	}

	private String extractAccessToken(final HttpServletRequest request) {
		final String authHeader = request.getHeader("Authorization");
		if (authHeader != null && authHeader.startsWith("Bearer ")) {
			return authHeader.substring(7);
		}

		return cookieUtil.extractCookie(request, "access_token").orElse(null);
	}
}
