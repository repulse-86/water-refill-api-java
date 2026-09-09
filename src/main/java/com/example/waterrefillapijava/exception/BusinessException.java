package com.example.waterrefillapijava.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

	private final String code;

	public BusinessException(String message, String code) {
		super(message);
		this.code = code;
	}

	public BusinessException(String message, String code, Throwable cause) {
		super(message, cause);
		this.code = code;
	}
}
