package com.rockburger.cartservice.configuration.exception;

import com.rockburger.cartservice.domain.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Global exception handler for consistent error responses across the application
 * This should be duplicated in both cart service and main burger service
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	// Service identifier - change this for each service
	private static final String SERVICE_NAME = "cart-service"; // Change to "burger-main" for main service

	// Cart-specific business exceptions
	@ExceptionHandler(CartNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleCartNotFoundException(CartNotFoundException ex, HttpServletRequest request) {
		logger.warn("Cart not found: {} for request {}", ex.getMessage(), request.getRequestURI());
		return createErrorResponse(
				HttpStatus.NOT_FOUND,
				ex.getMessage(),
				"CART_NOT_FOUND",
				request.getRequestURI()
		);
	}

	@ExceptionHandler(CartItemNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleCartItemNotFoundException(CartItemNotFoundException ex, HttpServletRequest request) {
		logger.warn("Cart item not found: {} for request {}", ex.getMessage(), request.getRequestURI());
		return createErrorResponse(
				HttpStatus.NOT_FOUND,
				ex.getMessage(),
				"CART_ITEM_NOT_FOUND",
				request.getRequestURI()
		);
	}

	@ExceptionHandler(DuplicateArticleException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateArticleException(DuplicateArticleException ex, HttpServletRequest request) {
		logger.warn("Duplicate article in cart: {} for request {}", ex.getMessage(), request.getRequestURI());
		return createErrorResponse(
				HttpStatus.CONFLICT,
				ex.getMessage(),
				"DUPLICATE_ARTICLE",
				request.getRequestURI()
		);
	}

	@ExceptionHandler(InvalidCartOperationException.class)
	public ResponseEntity<ErrorResponse> handleInvalidCartOperationException(InvalidCartOperationException ex, HttpServletRequest request) {
		logger.warn("Invalid cart operation: {} for request {}", ex.getMessage(), request.getRequestURI());
		return createErrorResponse(
				HttpStatus.BAD_REQUEST,
				ex.getMessage(),
				"INVALID_CART_OPERATION",
				request.getRequestURI()
		);
	}

	@ExceptionHandler(ConcurrentCartModificationException.class)
	public ResponseEntity<ErrorResponse> handleConcurrentCartModificationException(ConcurrentCartModificationException ex, HttpServletRequest request) {
		logger.warn("Concurrent cart modification: {} for request {}", ex.getMessage(), request.getRequestURI());
		return createErrorResponse(
				HttpStatus.CONFLICT,
				ex.getMessage(),
				"CONCURRENT_MODIFICATION",
				request.getRequestURI()
		);
	}

	@ExceptionHandler(CartTokensException.class)
	public ResponseEntity<ErrorResponse> handleCartTokensException(CartTokensException ex, HttpServletRequest request) {
		logger.warn("Cart token error: {} for request {}", ex.getMessage(), request.getRequestURI());

		// Determine if it's an expired token or invalid token
		HttpStatus status = ex.getMessage().toLowerCase().contains("expired") ?
				HttpStatus.UNAUTHORIZED : HttpStatus.UNAUTHORIZED;
		String errorCode = ex.getMessage().toLowerCase().contains("expired") ?
				"TOKEN_EXPIRED" : "INVALID_TOKEN";

		return createErrorResponse(status, ex.getMessage(), errorCode, request.getRequestURI());
	}

	// Authentication and Authorization exceptions
	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
		logger.warn("Authentication failed: {} for request {}", ex.getMessage(), request.getRequestURI());

		String message = "Authentication failed";
		String errorCode = "AUTHENTICATION_FAILED";

		if (ex.getMessage() != null) {
			if (ex.getMessage().contains("expired")) {
				message = "Your session has expired. Please log in again.";
				errorCode = "SESSION_EXPIRED";
			} else if (ex.getMessage().contains("invalid")) {
				message = "Invalid authentication credentials.";
				errorCode = "INVALID_CREDENTIALS";
			}
		}

		return createErrorResponse(HttpStatus.UNAUTHORIZED, message, errorCode, request.getRequestURI());
	}

	@ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleAuthenticationCredentialsNotFoundException(
			AuthenticationCredentialsNotFoundException ex, HttpServletRequest request) {
		logger.warn("Authentication credentials not found: {} for request {}", ex.getMessage(), request.getRequestURI());
		return createErrorResponse(
				HttpStatus.UNAUTHORIZED,
				"Authentication required. Please provide valid credentials.",
				"CREDENTIALS_REQUIRED",
				request.getRequestURI()
		);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
		logger.warn("Access denied: {} for request {}", ex.getMessage(), request.getRequestURI());
		return createErrorResponse(
				HttpStatus.FORBIDDEN,
				"Access denied. You don't have permission to access this resource.",
				"ACCESS_DENIED",
				request.getRequestURI()
		);
	}

	// Validation exceptions
	@ExceptionHandler(InvalidParameterException.class)
	public ResponseEntity<ErrorResponse> handleInvalidParameterException(InvalidParameterException ex, HttpServletRequest request) {
		logger.warn("Invalid parameter: {} for request {}", ex.getMessage(), request.getRequestURI());
		return createErrorResponse(
				HttpStatus.BAD_REQUEST,
				ex.getMessage(),
				"INVALID_PARAMETER",
				request.getRequestURI()
		);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
			MethodArgumentNotValidException ex, HttpServletRequest request) {
		logger.warn("Validation failed for request {}: {}", request.getRequestURI(), ex.getMessage());

		Map<String, String> validationErrors = new HashMap<>();
		ex.getBindingResult().getAllErrors().forEach(error -> {
			String fieldName = ((FieldError) error).getField();
			String errorMessage = error.getDefaultMessage();
			validationErrors.put(fieldName, errorMessage);
		});

		ErrorResponse errorResponse = createErrorResponseWithDetails(
				HttpStatus.BAD_REQUEST,
				"Validation failed",
				"VALIDATION_FAILED",
				request.getRequestURI(),
				validationErrors
		);

		return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolationException(
			ConstraintViolationException ex, HttpServletRequest request) {
		logger.warn("Constraint violation for request {}: {}", request.getRequestURI(), ex.getMessage());

		Map<String, String> validationErrors = new HashMap<>();
		Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
		for (ConstraintViolation<?> violation : violations) {
			String fieldName = violation.getPropertyPath().toString();
			String errorMessage = violation.getMessage();
			validationErrors.put(fieldName, errorMessage);
		}

		ErrorResponse errorResponse = createErrorResponseWithDetails(
				HttpStatus.BAD_REQUEST,
				"Constraint validation failed",
				"CONSTRAINT_VIOLATION",
				request.getRequestURI(),
				validationErrors
		);

		return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
	}

	// HTTP-related exceptions
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
			HttpMessageNotReadableException ex, HttpServletRequest request) {
		logger.warn("Malformed JSON request for {}: {}", request.getRequestURI(), ex.getMessage());
		return createErrorResponse(
				HttpStatus.BAD_REQUEST,
				"Malformed JSON request. Please check your request body.",
				"MALFORMED_JSON",
				request.getRequestURI()
		);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(
			HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
		logger.warn("Method not supported for {}: {}", request.getRequestURI(), ex.getMessage());
		return createErrorResponse(
				HttpStatus.METHOD_NOT_ALLOWED,
				"HTTP method '" + ex.getMethod() + "' is not supported for this endpoint.",
				"METHOD_NOT_ALLOWED",
				request.getRequestURI()
		);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
			MissingServletRequestParameterException ex, HttpServletRequest request) {
		logger.warn("Missing request parameter for {}: {}", request.getRequestURI(), ex.getMessage());
		return createErrorResponse(
				HttpStatus.BAD_REQUEST,
				"Missing required parameter: " + ex.getParameterName(),
				"MISSING_PARAMETER",
				request.getRequestURI()
		);
	}

	@ExceptionHandler(MissingPathVariableException.class)
	public ResponseEntity<ErrorResponse> handleMissingPathVariableException(
			MissingPathVariableException ex, HttpServletRequest request) {
		logger.warn("Missing path variable for {}: {}", request.getRequestURI(), ex.getMessage());
		return createErrorResponse(
				HttpStatus.BAD_REQUEST,
				"Missing required path variable: " + ex.getVariableName(),
				"MISSING_PATH_VARIABLE",
				request.getRequestURI()
		);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
			MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
		logger.warn("Type mismatch for {}: {}", request.getRequestURI(), ex.getMessage());
		return createErrorResponse(
				HttpStatus.BAD_REQUEST,
				"Invalid parameter type for '" + ex.getName() + "'. Expected: " + ex.getRequiredType().getSimpleName(),
				"TYPE_MISMATCH",
				request.getRequestURI()
		);
	}

	@ExceptionHandler(NoHandlerFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoHandlerFoundException(
			NoHandlerFoundException ex, HttpServletRequest request) {
		logger.warn("No handler found for {}: {}", request.getRequestURI(), ex.getMessage());
		return createErrorResponse(
				HttpStatus.NOT_FOUND,
				"The requested endpoint was not found.",
				"ENDPOINT_NOT_FOUND",
				request.getRequestURI()
		);
	}

	// Generic exceptions
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
			IllegalArgumentException ex, HttpServletRequest request) {
		logger.warn("Illegal argument for {}: {}", request.getRequestURI(), ex.getMessage());
		return createErrorResponse(
				HttpStatus.BAD_REQUEST,
				ex.getMessage(),
				"ILLEGAL_ARGUMENT",
				request.getRequestURI()
		);
	}

	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
		logger.error("Runtime exception for {}: {}", request.getRequestURI(), ex.getMessage(), ex);
		return createErrorResponse(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"An unexpected error occurred. Please try again later.",
				"INTERNAL_ERROR",
				request.getRequestURI()
		);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
		logger.error("Unexpected exception for {}: {}", request.getRequestURI(), ex.getMessage(), ex);
		return createErrorResponse(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"An unexpected error occurred. Please try again later.",
				"UNEXPECTED_ERROR",
				request.getRequestURI()
		);
	}

	// Helper methods
	private ResponseEntity<ErrorResponse> createErrorResponse(HttpStatus status, String message, String errorCode, String path) {
		ErrorResponse errorResponse = new ErrorResponse(
				LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
				status.value(),
				status.getReasonPhrase(),
				message,
				errorCode,
				path,
				SERVICE_NAME
		);

		return new ResponseEntity<>(errorResponse, status);
	}

	private ErrorResponse createErrorResponseWithDetails(HttpStatus status, String message, String errorCode, String path, Map<String, String> details) {
		return new ErrorResponse(
				LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
				status.value(),
				status.getReasonPhrase(),
				message,
				errorCode,
				path,
				SERVICE_NAME,
				details
		);
	}

	/**
	 * Standardized error response structure
	 */
	public static class ErrorResponse {
		private String timestamp;
		private int status;
		private String error;
		private String message;
		private String errorCode;
		private String path;
		private String service;
		private Map<String, String> details;

		public ErrorResponse(String timestamp, int status, String error, String message,
							 String errorCode, String path, String service) {
			this.timestamp = timestamp;
			this.status = status;
			this.error = error;
			this.message = message;
			this.errorCode = errorCode;
			this.path = path;
			this.service = service;
		}

		public ErrorResponse(String timestamp, int status, String error, String message,
							 String errorCode, String path, String service, Map<String, String> details) {
			this(timestamp, status, error, message, errorCode, path, service);
			this.details = details;
		}

		// Getters
		public String getTimestamp() { return timestamp; }
		public int getStatus() { return status; }
		public String getError() { return error; }
		public String getMessage() { return message; }
		public String getErrorCode() { return errorCode; }
		public String getPath() { return path; }
		public String getService() { return service; }
		public Map<String, String> getDetails() { return details; }

		// Setters
		public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
		public void setStatus(int status) { this.status = status; }
		public void setError(String error) { this.error = error; }
		public void setMessage(String message) { this.message = message; }
		public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
		public void setPath(String path) { this.path = path; }
		public void setService(String service) { this.service = service; }
		public void setDetails(Map<String, String> details) { this.details = details; }
	}
}