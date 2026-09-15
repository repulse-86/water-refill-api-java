package com.example.waterrefillapijava.report.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReconciliationResponse(
	String id,
	String date,

	@JsonProperty("expected_volume")
	BigDecimal expectedVolume,

	@JsonProperty("actual_throughput")
	BigDecimal actualThroughput,
	BigDecimal variance,

	@JsonProperty("variance_pct")
	BigDecimal variancePct,
	boolean flagged,
	String status
) {}
