package com.orqentra.payment.billing;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @GetMapping("/{orderReference}")
    public PaymentView byOrderReference(@PathVariable String orderReference) {
        return service.byOrderReference(orderReference);
    }

    @ExceptionHandler(UnknownPaymentException.class)
    public ProblemDetail unknownPayment(UnknownPaymentException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        body.setTitle("Unknown payment");
        return body;
    }
}
