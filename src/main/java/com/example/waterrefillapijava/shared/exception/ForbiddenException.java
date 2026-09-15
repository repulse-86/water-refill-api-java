package com.example.waterrefillapijava.shared.exception;

public class ForbiddenException extends BusinessException {

	public ForbiddenException(String message) {
		super(message, "FORBIDDEN");
	}
}
