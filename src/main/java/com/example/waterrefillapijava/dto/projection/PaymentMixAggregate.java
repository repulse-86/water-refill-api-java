package com.example.waterrefillapijava.dto.projection;

import java.math.BigDecimal;

public record PaymentMixAggregate(
	String paymentMethod,
	BigDecimal total
) {}
