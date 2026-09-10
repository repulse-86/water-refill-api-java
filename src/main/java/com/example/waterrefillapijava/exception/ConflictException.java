package com.example.waterrefillapijava.exception;

public class ConflictException extends BusinessException {

	public ConflictException(String message) {
		super(message, "CONFLICT");
	}

	public ConflictException(String message, Throwable cause) {
		super(message, "CONFLICT", cause);
	}
}
