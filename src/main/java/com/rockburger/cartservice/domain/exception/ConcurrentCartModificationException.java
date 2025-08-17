package com.rockburger.cartservice.domain.exception;

public class ConcurrentCartModificationException extends RuntimeException {
	public ConcurrentCartModificationException(String message) {
		super(message);
	}
}