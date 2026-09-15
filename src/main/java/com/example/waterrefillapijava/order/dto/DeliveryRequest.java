package com.example.waterrefillapijava.order.dto;

import java.math.BigDecimal;

import com.example.waterrefillapijava.order.model.DeliveryStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

public record DeliveryRequest(
	@JsonProperty("delivery_status")
	@NotNull(message = "The delivery status field is required.")
	DeliveryStatus deliveryStatus,

	@JsonProperty("bottles_returned")
	Integer bottlesReturned,

	@JsonProperty("cash_collected")
	BigDecimal cashCollected
) {
}
