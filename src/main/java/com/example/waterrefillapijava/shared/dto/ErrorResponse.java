package com.example.waterrefillapijava.shared.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ErrorResponse(
	int status,
	String code,
	String message,
	String path,
	String timestamp,
	Map<String, List<String>> errors
) {

	public static ErrorResponse of(
		final int status,
		final String code,
		final String message,
		final String path
	) {
		return new ErrorResponse(status, code, message, path, Instant.now().toString(), null);
	}

	public static ErrorResponse withErrors(
		final int status,
		final String code,
		final String message,
		final String path,
		final Map<String, List<String>> errors
	) {
		return new ErrorResponse(status, code, message, path, Instant.now().toString(), errors);
	}
}
