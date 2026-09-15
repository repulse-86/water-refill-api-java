package com.example.waterrefillapijava.order.dto.projection;

import java.math.BigDecimal;

public record ProductSalesAggregate(
	Long productId,
	String productName,
	String productType,
	long units,
	BigDecimal revenue
) {}
