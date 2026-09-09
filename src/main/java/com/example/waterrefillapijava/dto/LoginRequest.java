package com.example.waterrefillapijava.dto;

import jakarta.validation.constraints.NotNull;

public record LoginRequest(
	@NotNull String username,
	@NotNull String password,
	boolean remember
) {
	public LoginRequest {
	}
}