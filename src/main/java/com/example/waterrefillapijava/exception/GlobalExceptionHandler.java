package com.example.waterrefillapijava.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<?> handleApiException(final ApiException ex) {
		log.debug("ApiException: status={}, message={}", ex.getStatus(), ex.getMessage());
		return ResponseEntity.status(ex.getStatus())
			.body(Map.of(
				"message", ex.getMessage(),
				"timestamp", Instant.now().toString()
			));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<?> handleValidation(final MethodArgumentNotValidException ex) {
		final Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
			.collect(Collectors.toMap(
				field -> field.getField(),
				field -> field.getDefaultMessage() != null ? field.getDefaultMessage() : "Invalid value",
				(a, b) -> a,
				LinkedHashMap::new
			));

		return ResponseEntity.status(400)
			.body(Map.of(
				"message", "Validation failed",
				"errors", errors,
				"timestamp", Instant.now().toString()
			));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<?> handleGeneric(final Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(500)
			.body(Map.of(
				"message", "An internal error occurred",
				"timestamp", Instant.now().toString()
			));
	}
}
