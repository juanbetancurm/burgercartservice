package com.rockburger.cartservice.domain.exception;

public class CartTokensException extends RuntimeException {
    public CartTokensException(String message) {
        super(message);
    }

    public CartTokensException(String message, Throwable cause) {
        super(message, cause);
    }
}