package com.example.waterrefillapijava.order.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
	@JsonProperty("product_id")
	@NotNull(message = "The product field is required.")
	Long productId,

	@NotNull(message = "The quantity field is required.")
	@Positive(message = "The quantity must be a positive number.")
	Integer quantity,

	@JsonProperty("unit_price")
	BigDecimal unitPrice
) {
}
