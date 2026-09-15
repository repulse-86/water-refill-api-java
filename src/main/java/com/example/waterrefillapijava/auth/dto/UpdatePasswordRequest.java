package com.example.waterrefillapijava.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePasswordRequest(
	@NotBlank(message = "The current password field is required.")
	String currentPassword,

	@NotBlank(message = "The password field is required.")
	@Size(min = 6, message = "The password must be at least 6 characters.")
	String password,

	@NotBlank(message = "The password confirmation field is required.")
	String passwordConfirmation
) {
}
