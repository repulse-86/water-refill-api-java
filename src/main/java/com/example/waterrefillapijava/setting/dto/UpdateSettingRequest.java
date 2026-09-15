package com.example.waterrefillapijava.setting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateSettingRequest(
	@NotBlank(message = "The store name field is required.")
	@JsonProperty("store_name")
	String storeName,

	@JsonProperty("store_address")
	String storeAddress,

	@JsonProperty("store_phone")
	String storePhone,

	@NotBlank(message = "The currency field is required.")
	String currency,

	@NotNull(message = "The low stock threshold field is required.")
	@PositiveOrZero(message = "The low stock threshold must be a positive number.")
	@JsonProperty("low_stock_threshold")
	Integer lowStockThreshold
) {
}
