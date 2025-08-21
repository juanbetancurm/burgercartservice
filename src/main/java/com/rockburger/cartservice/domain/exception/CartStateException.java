package com.rockburger.cartservice.domain.exception;

/**
 * Exception thrown when a cart is in an invalid state for the requested operation.
 */
public class CartStateException extends CartBusinessException {

	public CartStateException(String message) {
		super(message);
	}

	public CartStateException(String message, Throwable cause) {
		super(message, cause);
	}

	public CartStateException(Throwable cause) {
		super(cause);
	}
}