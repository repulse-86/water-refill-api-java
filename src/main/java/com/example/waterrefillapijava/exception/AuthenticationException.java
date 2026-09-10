package com.example.waterrefillapijava.exception;

public class AuthenticationException extends BusinessException {

	public AuthenticationException(String message) {
		super(message, "UNAUTHENTICATED");
	}
}
