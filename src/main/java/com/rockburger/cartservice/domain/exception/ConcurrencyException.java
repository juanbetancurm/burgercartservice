package com.rockburger.cartservice.domain.exception;

/**
 * Exception thrown when a cart operation fails due to concurrency issues
 * after multiple retry attempts.
 */
public class ConcurrencyException extends RuntimeException {

	public ConcurrencyException(String message) {
		super(message);
	}

	public ConcurrencyException(String message, Throwable cause) {
		super(message, cause);
	}

	public ConcurrencyException(Throwable cause) {
		super(cause);
	}
}
