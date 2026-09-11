package com.example.waterrefillapijava.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DebtAgingResponse(
	Long id,
	String name,
	String phone,

	@JsonProperty("subscriber_status")
	String subscriberStatus,

	@JsonProperty("bottle_debt")
	int bottleDebt,

	@JsonProperty("outstanding_balance")
	BigDecimal outstandingBalance,
	BigDecimal total
) {}
