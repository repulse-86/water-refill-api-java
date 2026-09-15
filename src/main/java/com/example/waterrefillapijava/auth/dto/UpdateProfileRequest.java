package com.example.waterrefillapijava.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
	@NotBlank(message = "The username field is required.")
	@Size(min = 3, message = "The username must be at least 3 characters.")
	String username
) {
}
