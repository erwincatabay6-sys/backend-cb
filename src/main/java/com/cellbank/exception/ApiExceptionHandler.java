package com.cellbank.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;

@RestControllerAdvice
public class ApiExceptionHandler {

    // -----------------------------
    // FIELD VALIDATION
    // -----------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(
            MethodArgumentNotValidException exception) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();

        for (FieldError error :
                exception.getBindingResult().getFieldErrors()) {

            String message = error.getDefaultMessage();

            fieldErrors.putIfAbsent(
                    error.getField(),
                    message != null ? message : "Invalid value."
            );
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Check the submitted fields."
        );

        problem.setTitle("Validation failed");
        problem.setProperty("errors", fieldErrors);

        return problem;
    }

    // -----------------------------
    // INVALID REQUEST BODY
    // -----------------------------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableRequest(
            HttpMessageNotReadableException exception) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The request body is missing or invalid. "
                        + "Send valid JSON with the expected field types."
        );

        problem.setTitle("Invalid request body");

        return problem;
    }

    // -----------------------------
    // EXPLICIT HTTP ERRORS
    // -----------------------------

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(
            ResponseStatusException exception) {

        String detail = exception.getReason();

        if (detail == null || detail.isBlank()) {
            detail = "The request could not be completed.";
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                exception.getStatusCode(),
                detail
        );

        return ResponseEntity
                .status(exception.getStatusCode())
                .headers(exception.getHeaders())
                .body(problem);
    }

    // -----------------------------
    // DATABASE CONFLICTS
    // -----------------------------

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataConflict(
            DataIntegrityViolationException exception) {

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "The requested change conflicts with stored data."
        );
    }
}