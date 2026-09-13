package com.example.waterrefillapijava.dto.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MeterReadingValue(
	LocalDate readingDate,
	BigDecimal meterValue
) {}
