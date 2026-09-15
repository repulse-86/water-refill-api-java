package com.example.waterrefillapijava.order.dto.projection;

import java.math.BigDecimal;

public record PaymentMixAggregate(
	String paymentMethod,
	BigDecimal total
) {}
