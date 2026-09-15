package com.example.waterrefillapijava.meterreading.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

public record MeterReadingRequest(
	@JsonProperty("reading_date")
	@NotNull(message = "The reading date field is required.")
	String readingDate,

	@JsonProperty("meter_value")
	@NotNull(message = "The meter value field is required.")
	BigDecimal meterValue,

	String notes
) {
}
