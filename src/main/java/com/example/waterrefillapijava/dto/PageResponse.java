package com.example.waterrefillapijava.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PageResponse<T>(
	List<T> data,

	@JsonProperty("current_page")
	int currentPage,

	@JsonProperty("per_page")
	int perPage,

	@JsonProperty("total_items")
	long totalItems,

	@JsonProperty("total_pages")
	int totalPages
) {
}
