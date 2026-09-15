package com.example.waterrefillapijava.meterreading.dto.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MeterReadingValue(
	LocalDate readingDate,
	BigDecimal meterValue
) {}
