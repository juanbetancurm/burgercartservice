package com.rockburger.cartservice.configuration.exceptionhandler;

import com.rockburger.cartservice.domain.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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

    @ExceptionHandler(CartNotFoundException.class)
    public ResponseEntity<ExceptionResponseCart> handleCartNotFoundException(
            CartNotFoundException exception) {
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
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ExceptionResponseCart(
                        "An unexpected error occurred",
                        HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                        LocalDateTime.now()
                ));
    }
}