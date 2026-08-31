package com.wrx.booking.api;

import com.wrx.booking.api.dto.ErrorResponse;
import com.wrx.booking.support.ErrorCode;
import com.wrx.booking.support.TraceContext;
import com.wrx.booking.auth.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException exception,
            HttpServletRequest request
    ) {
        return buildResponse(ErrorCode.UNAUTHORIZED, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String reason = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));

        return buildResponse(ErrorCode.INVALID_REQUEST, reason, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        ErrorCode code = exception.getMessage() != null && exception.getMessage().startsWith("slot not found")
                ? ErrorCode.RESOURCE_NOT_FOUND : ErrorCode.INVALID_REQUEST;

        return buildResponse(code, exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "event=api.exception traceId={} path={} exception={} reason={}",
                TraceContext.traceId(),
                request.getRequestURI(),
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                exception
        );

        return buildResponse(ErrorCode.INTERNAL_ERROR, exception.getMessage(), request);
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            ErrorCode code,
            String reason,
            HttpServletRequest request
    ) {
        String safeReason = reason == null || reason.isBlank() ? code.message() : reason;

        log.warn(
                "event=api.error traceId={} code={} path={} reason={}",
                TraceContext.traceId(),
                code.code(),
                request.getRequestURI(),
                safeReason
        );

        ErrorResponse response = new ErrorResponse(
                code.code(),
                code.message(),
                safeReason,
                TraceContext.traceId(),
                request.getRequestURI(),
                Instant.now()
        );

        return ResponseEntity.status(code.httpStatus()).body(response);
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
