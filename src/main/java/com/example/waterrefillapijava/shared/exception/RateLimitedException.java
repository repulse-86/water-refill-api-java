package com.example.waterrefillapijava.shared.exception;

public class RateLimitedException extends BusinessException {

	public RateLimitedException(String message) {
		super(message, "RATE_LIMITED");
	}
}
