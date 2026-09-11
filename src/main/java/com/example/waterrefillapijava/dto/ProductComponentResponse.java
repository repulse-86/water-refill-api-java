package com.example.waterrefillapijava.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProductComponentResponse(
	Long id,

	@JsonProperty("product_id")
	Long productId,

	@JsonProperty("component_id")
	Long componentId,

	@JsonProperty("component_name")
	String componentName,

	Integer quantity
) {
}
