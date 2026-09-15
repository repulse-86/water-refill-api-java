package com.example.waterrefillapijava.order.dto;

import com.example.waterrefillapijava.order.model.OrderStatus;

import jakarta.validation.constraints.NotNull;

public record StatusRequest(
	@NotNull(message = "The status field is required.")
	OrderStatus status
) {
}
