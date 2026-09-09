package com.example.waterrefillapijava.exception;

public class ForbiddenException extends BusinessException {

	public ForbiddenException(String message) {
		super(message, "FORBIDDEN");
	}
}
