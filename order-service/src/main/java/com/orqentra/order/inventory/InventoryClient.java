package com.orqentra.order.inventory;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient(RestClient inventoryRestClient) {
        this.restClient = inventoryRestClient;
    }

    public void deduct(List<StockLine> lines) {
        post("/api/inventory/deductions", lines);
    }

    public void release(List<StockLine> lines) {
        post("/api/inventory/releases", lines);
    }

    private void post(String path, List<StockLine> lines) {
        try {
            restClient.post()
                    .uri(path)
                    .body(Map.of("items", lines))
                    .retrieve()
                    .onStatus(status -> status.value() == 409, (request, response) -> {
                        throw new InsufficientStockException(
                                "Inventory rejected the deduction: " + new String(
                                        response.getBody().readAllBytes()));
                    })
                    .onStatus(status -> status.isError(), (request, response) -> {
                        throw new InventoryUnavailableException(
                                "Inventory service returned " + response.getStatusCode()
                                + " for " + path);
                    })
                    .toBodilessEntity();
        } catch (InsufficientStockException | InventoryUnavailableException ex) {
            throw ex;
        } catch (RestClientException ex) {
            // Connection refused, read timeout, unreadable body: the deduction may or may
            // not have happened, so this is never reported as a stock shortage.
            throw new InventoryUnavailableException(
                    "Could not reach the inventory service for " + path, ex);
        }
    }
}
