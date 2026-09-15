package com.example.waterrefillapijava.report.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DailySalesRowResponse(
	String id,
	String date,

	@JsonProperty("order_count")
	int orderCount,
	BigDecimal revenue,
	BigDecimal cash,

	@JsonProperty("e_wallet")
	BigDecimal eWallet,
	BigDecimal credit,
	BigDecimal gallons
) {}
