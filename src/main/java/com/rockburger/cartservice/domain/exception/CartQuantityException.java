package com.rockburger.cartservice.domain.exception;

/**
 * Exception thrown when invalid quantities are used in cart operations.
 */
public class CartQuantityException extends CartBusinessException {

	public CartQuantityException(String message) {
		super(message);
	}

	public CartQuantityException(String message, Throwable cause) {
		super(message, cause);
	}

	public CartQuantityException(Throwable cause) {
		super(cause);
	}
}