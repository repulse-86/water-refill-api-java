package com.example.waterrefillapijava.shared.config;

import com.example.waterrefillapijava.shared.dto.ErrorResponse;
import com.example.waterrefillapijava.shared.exception.BusinessException;
import com.example.waterrefillapijava.shared.exception.FieldValidationException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(final MethodArgumentNotValidException e, final HttpServletRequest request) {
		final Map<String, List<String>> fieldErrors = new HashMap<>();
		for (org.springframework.validation.FieldError fieldError : e.getBindingResult().getFieldErrors()) {
			fieldErrors.computeIfAbsent(fieldError.getField(), k -> new ArrayList<>()).add(fieldError.getDefaultMessage());
		}
		final String message = fieldErrors.values().stream()
			.findFirst()
			.flatMap(list -> list.stream().findFirst())
			.orElse("Validation failed");

		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
			.body(ErrorResponse.withErrors(HttpStatus.UNPROCESSABLE_ENTITY.value(), "VALIDATION_FAILED", message, pathOf(request), fieldErrors));
	}

	@ExceptionHandler(FieldValidationException.class)
	public ResponseEntity<ErrorResponse> handleFieldValidation(final FieldValidationException e, final HttpServletRequest request) {
		final String message = e.getErrors().values().stream()
			.findFirst()
			.flatMap(list -> list.stream().findFirst())
			.orElse("Validation failed");

		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
			.body(ErrorResponse.withErrors(HttpStatus.UNPROCESSABLE_ENTITY.value(), "VALIDATION_FAILED", message, pathOf(request), e.getErrors()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadable(final HttpMessageNotReadableException e, final HttpServletRequest request) {
		return ResponseEntity.badRequest()
			.body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "MALFORMED_REQUEST", "Malformed request body", pathOf(request)));
	}

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(final BusinessException e, final HttpServletRequest request) {
		final HttpStatus status = switch (e.getCode()) {
			case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
			case "CONFLICT" -> HttpStatus.CONFLICT;
			case "UNAUTHENTICATED" -> HttpStatus.UNAUTHORIZED;
			case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
			case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
			default -> {
				log.warn("Unmapped BusinessException code '{}' defaulting to 400", e.getCode());
				yield HttpStatus.BAD_REQUEST;
			}
		};
		return ResponseEntity.status(status)
			.body(ErrorResponse.of(status.value(), e.getCode(), e.getMessage(), pathOf(request)));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrity(final DataIntegrityViolationException e, final HttpServletRequest request) {
		log.error("Data integrity violation", e);
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(ErrorResponse.of(HttpStatus.CONFLICT.value(), "CONFLICT", "Operation conflicts with existing data", pathOf(request)));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGeneric(final Exception e, final HttpServletRequest request) {
		log.error("Unhandled exception", e);
		return ResponseEntity.internalServerError()
			.body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR", "An unexpected error occurred", pathOf(request)));
	}

	private String pathOf(final HttpServletRequest request) {
		return request != null ? request.getRequestURI() : null;
	}
}
