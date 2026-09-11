package com.example.waterrefillapijava.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductComponentRequest(
	@JsonProperty("component_id")
	@NotNull(message = "The component field is required.")
	Long componentId,

	@NotNull(message = "The quantity field is required.")
	@Positive(message = "The quantity must be a positive number.")
	Integer quantity
) {
}
