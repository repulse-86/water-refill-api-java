package com.example.waterrefillapijava.report.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProductPerformanceResponse(
	Long id,

	@JsonProperty("product_id")
	Long productId,
	String name,
	String type,
	int units,
	BigDecimal revenue,

	@JsonProperty("share_pct")
	BigDecimal sharePct
) {}
