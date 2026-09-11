package com.example.waterrefillapijava.dto;

import com.example.waterrefillapijava.model.OrderStatus;

import jakarta.validation.constraints.NotNull;

public record StatusRequest(
	@NotNull(message = "The status field is required.")
	OrderStatus status
) {
}
