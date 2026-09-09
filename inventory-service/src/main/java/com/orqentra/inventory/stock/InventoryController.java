package com.orqentra.inventory.stock;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/{sku}")
    public StockView bySku(@PathVariable String sku) {
        return new StockView(sku, service.availableFor(sku));
    }

    @PostMapping("/deductions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deduct(@RequestBody DeductRequest request) {
        service.deduct(request.items());
    }

    @PostMapping("/releases")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@RequestBody DeductRequest request) {
        service.release(request.items());
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail insufficientStock(InsufficientStockException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        body.setTitle("Insufficient stock");
        return body;
    }
}
