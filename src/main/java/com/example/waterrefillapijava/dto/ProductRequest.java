package com.example.waterrefillapijava.dto;

import java.math.BigDecimal;

import com.example.waterrefillapijava.model.ProductType;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductRequest(
	@NotBlank(message = "The name field is required.")
	@Size(min = 2, message = "The name must be at least 2 characters.")
	String name,

	@NotNull(message = "The type field is required.")
	ProductType type,

	@JsonProperty("volume_gallons")
	BigDecimal volumeGallons,

	@NotNull(message = "The price field is required.")
	@PositiveOrZero(message = "The price must be a positive number.")
	BigDecimal price,

	@NotNull(message = "The stock quantity field is required.")
	@PositiveOrZero(message = "The stock quantity must be a positive number.")
	@JsonProperty("stock_quantity")
	Integer stockQuantity,

	@NotNull(message = "The reorder point field is required.")
	@PositiveOrZero(message = "The reorder point must be a positive number.")
	@JsonProperty("reorder_point")
	Integer reorderPoint,

	String image
) {
}
