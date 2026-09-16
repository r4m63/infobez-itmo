package com.itmo.infobezitmo.controller;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Единый формат ошибок (ProblemDetail) без стектрейсов и внутренних деталей. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail statusException(ResponseStatusException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ProblemDetail invalidInput() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    }
}
