package com.example.waterrefillapijava.order.dto.projection;

import java.math.BigDecimal;

public record OrderItemProjection(
	Long orderId,
	Long productId,
	String productName,
	int quantity,
	BigDecimal unitPrice,
	BigDecimal subtotal
) {}
