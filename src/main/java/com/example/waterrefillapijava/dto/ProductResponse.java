package com.example.waterrefillapijava.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.waterrefillapijava.model.ProductType;

public record ProductResponse(
	Long id,

	String name,

	ProductType type,

	@JsonProperty("volume_gallons")
	BigDecimal volumeGallons,

	BigDecimal price,

	@JsonProperty("stock_quantity")
	Integer stockQuantity,

	@JsonProperty("reorder_point")
	Integer reorderPoint,

	String image
) {
}
