package com.example.waterrefillapijava.shared.exception;

public class AuthenticationException extends BusinessException {

	public AuthenticationException(String message) {
		super(message, "UNAUTHENTICATED");
	}
}
