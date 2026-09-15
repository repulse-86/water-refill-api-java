package com.example.waterrefillapijava.customer.dto;

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
	BigDecimal outstandingBalance,

	@JsonProperty("deleted_at")
	String deletedAt
) {
}
