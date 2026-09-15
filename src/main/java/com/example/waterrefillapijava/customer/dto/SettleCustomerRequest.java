package com.example.waterrefillapijava.customer.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.PositiveOrZero;

public record SettleCustomerRequest(
	@PositiveOrZero(message = "The bottle return must be a positive number.")
	@JsonProperty("bottle_return")
	Integer bottleReturn,

	@PositiveOrZero(message = "The cash payment must be a positive number.")
	@JsonProperty("cash_payment")
	BigDecimal cashPayment
) {
}
