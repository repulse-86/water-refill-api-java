package com.example.waterrefillapijava.dto;

import java.util.Map;

public record ErrorResponse(String message, Map<String, String> errors) {
	public static ErrorResponse of(String message) {
		return new ErrorResponse(message, Map.of());
	}

	public static ErrorResponse withErrors(String message, Map<String, String> errors) {
		return new ErrorResponse(message, errors);
	}
}
