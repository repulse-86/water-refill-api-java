package com.example.waterrefillapijava.order.dto.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySalesAggregate(
	java.sql.Date date,
	Long orderCount,
	BigDecimal revenue,
	BigDecimal cash,
	BigDecimal eWallet,
	BigDecimal credit
) {
	public LocalDate localDate() {
		return date.toLocalDate();
	}
}
