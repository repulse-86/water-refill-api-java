package com.example.waterrefillapijava.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MeterReadingResponse(
	Long id,

	@JsonProperty("reading_date")
	String readingDate,

	@JsonProperty("meter_value")
	BigDecimal meterValue,

	String notes,

	@JsonProperty("previous_reading_value")
	BigDecimal previousReadingValue,

	@JsonProperty("expected_volume")
	BigDecimal expectedVolume,

	@JsonProperty("actual_throughput")
	BigDecimal actualThroughput,

	BigDecimal variance,

	@JsonProperty("variance_pct")
	BigDecimal variancePct,

	Boolean flagged,

	@JsonProperty("created_at")
	String createdAt,

	@JsonProperty("updated_at")
	String updatedAt,

	@JsonProperty("deleted_at")
	String deletedAt
) {
}
