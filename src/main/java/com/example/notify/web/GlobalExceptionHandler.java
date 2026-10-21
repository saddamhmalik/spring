package com.example.notify.web;

import com.stripe.exception.StripeException;
import com.twilio.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception,
                                                     HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Request failed: {} {} - {}", request.getMethod(), request.getRequestURI(), message);
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException exception,
                                                         HttpServletRequest request) {
        log.warn("Request failed: {} {} - unreadable body", request.getMethod(), request.getRequestURI(), exception);
        return build(HttpStatus.BAD_REQUEST, "Request body is missing or malformed.", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException exception,
                                                           HttpServletRequest request) {
        String message = exception.getParameterName() + " is required.";
        log.warn("Request failed: {} {} - {}", request.getMethod(), request.getRequestURI(), message);
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ApiError> handleThirdPartyClientError(HttpClientErrorException exception,
                                                                HttpServletRequest request) {
        log.error("Third party rejected request: {} {} - {} {}",
                request.getMethod(), request.getRequestURI(),
                exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
        return build(HttpStatus.BAD_REQUEST, exception.getResponseBodyAsString(), request);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiError> handleThirdPartyFailure(RestClientException exception,
                                                            HttpServletRequest request) {
        log.error("Third party call failed: {} {}",
                request.getMethod(), request.getRequestURI(), exception);
        return build(HttpStatus.BAD_GATEWAY, exception.getMessage(), request);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleTwilioError(ApiException exception, HttpServletRequest request) {
        log.error("Twilio call failed: {} {} - code {}",
                request.getMethod(), request.getRequestURI(), exception.getCode(), exception);
        return build(HttpStatus.BAD_GATEWAY, exception.getMessage(), request);
    }

    @ExceptionHandler(MailException.class)
    public ResponseEntity<ApiError> handleMailError(MailException exception, HttpServletRequest request) {
        log.error("Mail delivery failed: {} {}",
                request.getMethod(), request.getRequestURI(), exception);
        return build(HttpStatus.BAD_GATEWAY, exception.getMessage(), request);
    }

    @ExceptionHandler(StripeException.class)
    public ResponseEntity<ApiError> handleStripeError(StripeException exception, HttpServletRequest request) {
        log.error("Stripe call failed: {} {} - code {}",
                request.getMethod(), request.getRequestURI(), exception.getCode(), exception);
        return build(HttpStatus.BAD_GATEWAY, exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedError(Exception exception, HttpServletRequest request) {
        log.error("Unexpected failure: {} {}",
                request.getMethod(), request.getRequestURI(), exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage(), request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
                        message, request.getRequestURI()));
    }

    public record ApiError(Instant timestamp, int status, String error, String message, String path) {
    }
}
