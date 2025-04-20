package com.rockburger.cartservice.configuration.exceptionhandler;

import com.rockburger.cartservice.domain.exception.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@RequiredArgsConstructor
public class ControllerAdvisorCart {
    private static final Logger logger = LoggerFactory.getLogger(ControllerAdvisorCart.class);

    @ExceptionHandler(CartNotFoundException.class)
    public ResponseEntity<ExceptionResponseCart> handleCartNotFoundException(
            CartNotFoundException exception) {
        logger.warn("Cart not found: {}", exception.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ExceptionResponseCart(
                        exception.getMessage(),
                        HttpStatus.NOT_FOUND.toString(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(CartItemNotFoundException.class)
    public ResponseEntity<ExceptionResponseCart> handleCartItemNotFoundException(
            CartItemNotFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ExceptionResponseCart(
                        exception.getMessage(),
                        HttpStatus.NOT_FOUND.toString(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(DuplicateArticleException.class)
    public ResponseEntity<ExceptionResponseCart> handleDuplicateArticleException(
            DuplicateArticleException exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ExceptionResponseCart(
                        exception.getMessage(),
                        HttpStatus.CONFLICT.toString(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ExceptionResponseCart> handleIllegalStateException(
            IllegalStateException exception) {
        logger.error("Illegal state: {}", exception.getMessage(), exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ExceptionResponseCart(
                        exception.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ExceptionResponseCart> handleAuthenticationException(
            AuthenticationException exception) {
        logger.error("Authentication error: {}", exception.getMessage(), exception);
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ExceptionResponseCart(
                        "Authentication failed: " + exception.getMessage(),
                        HttpStatus.UNAUTHORIZED.toString(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(CartTokensException.class)
    public ResponseEntity<ExceptionResponseCart> handleCartTokensException(
            CartTokensException exception) {
        logger.error("Token error: {}", exception.getMessage(), exception);
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ExceptionResponseCart(
                        exception.getMessage(),
                        HttpStatus.UNAUTHORIZED.toString(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(InvalidCartOperationException.class)
    public ResponseEntity<ExceptionResponseCart> handleInvalidCartOperationException(
            InvalidCartOperationException exception) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ExceptionResponseCart(
                        exception.getMessage(),
                        HttpStatus.BAD_REQUEST.toString(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(ConcurrentModificationException.class)
    public ResponseEntity<ExceptionResponseCart> handleConcurrentModificationException(
            ConcurrentModificationException exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ExceptionResponseCart(
                        exception.getMessage(),
                        HttpStatus.CONFLICT.toString(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(
            MethodArgumentNotValidException exception) {
        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ExceptionResponseCart> handleAccessDeniedException(
            AccessDeniedException exception) {
        logger.error("Access denied: {}", exception.getMessage(), exception);
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ExceptionResponseCart(
                        "Insufficient permissions",
                        HttpStatus.FORBIDDEN.toString(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponseCart> handleGlobalException(Exception exception) {
        logger.error("Unexpected error: {}", exception.getMessage(), exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ExceptionResponseCart(
                        "An unexpected error occurred: " + exception.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                        LocalDateTime.now()
                ));
    }
}