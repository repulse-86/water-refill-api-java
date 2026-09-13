package com.example.waterrefillapijava.dto.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GallonsByDate(
	java.sql.Date date,
	BigDecimal gallons
) {
	public LocalDate localDate() {
		return date.toLocalDate();
	}
}
