package com.rockburger.cartservice.domain.exception;

public class DuplicateArticleException extends RuntimeException {
    public DuplicateArticleException(String message) {
        super(message);
    }
}