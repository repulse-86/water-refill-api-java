package com.example.waterrefillapijava.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
	@NotBlank(message = "The name field is required.")
	@Size(min = 2, message = "The name must be at least 2 characters.")
	String name,

	@NotBlank(message = "The phone field is required.")
	@Pattern(regexp = "^09\\d{9}$", message = "The phone must be a valid 11-digit mobile number starting with 09.")
	String phone,

	@NotBlank(message = "The email field is required.")
	@Email(message = "The email must be a valid email address.")
	String email
) {
}
