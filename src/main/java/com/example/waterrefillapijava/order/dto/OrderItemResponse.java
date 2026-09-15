package com.example.waterrefillapijava.order.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderItemResponse(
	Long id,
	@JsonProperty("product_id")
	Long productId,
	@JsonProperty("product_name")
	String productName,
	Integer quantity,
	@JsonProperty("unit_price")
	BigDecimal unitPrice,
	BigDecimal subtotal
) {
}
