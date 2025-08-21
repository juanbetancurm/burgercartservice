package com.rockburger.cartservice.domain.exception;

/**
 * Exception thrown when cart validation fails.
 * This includes validation of cart data, items, quantities, etc.
 */
public class CartValidationException extends RuntimeException {

	public CartValidationException(String message) {
		super(message);
	}

	public CartValidationException(String message, Throwable cause) {
		super(message, cause);
	}

	public CartValidationException(Throwable cause) {
		super(cause);
	}
}