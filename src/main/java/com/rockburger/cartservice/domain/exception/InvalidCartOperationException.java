package com.rockburger.cartservice.domain.exception;

/**
 * Exception thrown when an invalid cart operation is attempted.
 * This includes operations that violate business rules or cart state constraints.
 *
 * Examples of invalid operations:
 * - Adding items to an abandoned cart
 * - Modifying a completed cart
 * - Performing operations on a cart that doesn't belong to the user
 * - Attempting operations that violate cart business rules
 */
public class InvalidCartOperationException extends RuntimeException {

    /**
     * Constructs a new InvalidCartOperationException with the specified detail message.
     *
     * @param message the detail message explaining why the cart operation is invalid
     */
    public InvalidCartOperationException(String message) {
        super(message);
    }

    /**
     * Constructs a new InvalidCartOperationException with the specified detail message
     * and cause.
     *
     * @param message the detail message explaining why the cart operation is invalid
     * @param cause the cause of this exception
     */
    public InvalidCartOperationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new InvalidCartOperationException with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public InvalidCartOperationException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new InvalidCartOperationException with the specified detail message,
     * cause, suppression enabled or disabled, and writable stack trace enabled or disabled.
     *
     * @param message the detail message
     * @param cause the cause
     * @param enableSuppression whether or not suppression is enabled or disabled
     * @param writableStackTrace whether or not the stack trace should be writable
     */
    protected InvalidCartOperationException(String message, Throwable cause,
                                            boolean enableSuppression,
                                            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}