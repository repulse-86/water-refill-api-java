package com.example.waterrefillapijava.exception;

import java.util.Map;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

	private final int status;

	private ApiException(final int status, final String message) {
		super(message);
		this.status = status;
	}

	public static ApiException status(final int status, final String message) {
		return new ApiException(status, message);
	}

	public static ApiException notFound(final String message) {
		return new ApiException(404, message);
	}

	public static ApiException badRequest(final String message) {
		return new ApiException(400, message);
	}

	public static ApiException conflict(final String message) {
		return new ApiException(409, message);
	}

	public static ApiException unauthenticated(final String message) {
		return new ApiException(401, message);
	}

	public static ApiException forbidden(final String message) {
		return new ApiException(403, message);
	}

	public static ApiException withErrors(final int status, final String message, final Map<String, String> errors) {
		return new ApiException(status, message);
	}
}
