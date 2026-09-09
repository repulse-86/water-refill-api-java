package com.example.waterrefillapijava.dto;

import java.util.Map;

public record LoginResponse(
	String token,
	Map<String, Object> user
) {
	public static LoginResponse of(String token, Long id, String username) {
		return new LoginResponse(
			token,
			Map.of("id", id, "username", username)
		);
	}
}
