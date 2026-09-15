package com.example.waterrefillapijava.order.dto.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PendingOrderProjection(
	Long id,
	String customerName,
	String orderType,
	String status,
	BigDecimal totalAmount,
	LocalDateTime createdAt
) {}
