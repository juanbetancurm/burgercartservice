package com.rockburger.cartservice.domain.exception;

/**
 * Exception thrown when a cart operation fails due to concurrent modification
 * by another transaction or thread.
 */
public class ConcurrentModificationException extends RuntimeException {

    public ConcurrentModificationException(String message) {
        super(message);
    }

    public ConcurrentModificationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConcurrentModificationException(Throwable cause) {
        super(cause);
    }
}