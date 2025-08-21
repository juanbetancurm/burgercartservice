package com.rockburger.cartservice.domain.exception;

/**
 * Exception thrown when cart item validation fails.
 */
public class CartItemValidationException extends CartBusinessException {

	public CartItemValidationException(String message) {
		super(message);
	}

	public CartItemValidationException(String message, Throwable cause) {
		super(message, cause);
	}

	public CartItemValidationException(Throwable cause) {
		super(cause);
	}
}