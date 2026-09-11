package com.example.waterrefillapijava.dto;

import com.example.waterrefillapijava.model.DeliveryStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

public record DeliveryRequest(
	@JsonProperty("delivery_status")
	@NotNull(message = "The delivery status field is required.")
	DeliveryStatus deliveryStatus,

	@JsonProperty("bottles_returned")
	Integer bottlesReturned,

	@JsonProperty("cash_collected")
	Double cashCollected
) {
}
