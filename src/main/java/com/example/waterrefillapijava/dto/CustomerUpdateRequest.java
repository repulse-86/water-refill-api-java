package com.example.waterrefillapijava.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CustomerUpdateRequest(
	@NotBlank(message = "The name field is required.")
	@Size(min = 2, message = "The name must be at least 2 characters.")
	String name,

	@NotBlank(message = "The phone field is required.")
	@Pattern(regexp = "^09\\d{9}$", message = "The phone must be a valid 11-digit mobile number starting with 09.")
	String phone,

	@NotBlank(message = "The email field is required.")
	@Email(message = "The email must be a valid email address.")
	String email,

	@NotBlank(message = "The subscriber status field is required.")
	@JsonProperty("subscriber_status")
	String subscriberStatus,

	@NotNull(message = "The bottle debt field is required.")
	@PositiveOrZero(message = "The bottle debt must be a positive number.")
	@JsonProperty("bottle_debt")
	Integer bottleDebt,

	@NotNull(message = "The outstanding balance field is required.")
	@PositiveOrZero(message = "The outstanding balance must be a positive number.")
	@JsonProperty("outstanding_balance")
	BigDecimal outstandingBalance
) {
}
