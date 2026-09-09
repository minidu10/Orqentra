package com.orqentra.order.support;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.orqentra.order.catalog.UnknownSkuException;
import com.orqentra.order.ordering.UnknownOrderException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UnknownSkuException.class)
    public ProblemDetail unknownSku(UnknownSkuException ex) {
        return problem(HttpStatus.NOT_FOUND, "Unknown SKU", ex.getMessage());
    }

    @ExceptionHandler(UnknownOrderException.class)
    public ProblemDetail unknownOrder(UnknownOrderException ex) {
        return problem(HttpStatus.NOT_FOUND, "Unknown order", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        return body;
    }
}