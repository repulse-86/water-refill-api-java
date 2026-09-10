package com.example.waterrefillapijava.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CustomerResponse(
	Long id,

	String name,

	String phone,

	String email,

	@JsonProperty("subscriber_status")
	String subscriberStatus,

	@JsonProperty("bottle_debt")
	Integer bottleDebt,

	@JsonProperty("outstanding_balance")
	BigDecimal outstandingBalance
) {
}
