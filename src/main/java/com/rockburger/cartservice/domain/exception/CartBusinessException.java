package com.rockburger.cartservice.domain.exception;

/**
 * Base exception for all cart business logic related exceptions.
 * This provides a common parent for all cart domain exceptions.
 */
public abstract class CartBusinessException extends RuntimeException {

	protected CartBusinessException(String message) {
		super(message);
	}

	protected CartBusinessException(String message, Throwable cause) {
		super(message, cause);
	}

	protected CartBusinessException(Throwable cause) {
		super(cause);
	}
}