package com.example.waterrefillapijava.shared.exception;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;

@Getter
public class FieldValidationException extends RuntimeException {

	private final Map<String, List<String>> errors;

	private FieldValidationException(Map<String, List<String>> errors) {
		super("Validation failed");
		this.errors = errors;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private final Map<String, List<String>> errors = new LinkedHashMap<>();

		public Builder add(String field, String message) {
			errors.computeIfAbsent(field, k -> new ArrayList<>()).add(message);
			return this;
		}

		public FieldValidationException build() {
			return new FieldValidationException(errors);
		}
	}
}
