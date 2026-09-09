package com.orqentra.order.ordering;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.orqentra.order.events.StockRejectedEvent;
import com.orqentra.order.events.StockReservedEvent;
import com.orqentra.order.events.Topics;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final OrderService orders;

    public OrderEventListener(OrderService orders) {
        this.orders = orders;
    }

    @KafkaListener(topics = Topics.STOCK_RESERVED)
    public void onStockReserved(StockReservedEvent event) {
        log.info("Stock reserved for order {}", event.orderReference());
        orders.markConfirmed(event.orderReference());
    }

    @KafkaListener(topics = Topics.STOCK_REJECTED)
    public void onStockRejected(StockRejectedEvent event) {
        log.info("Stock rejected for order {}: {}", event.orderReference(), event.reason());
        orders.markCancelled(event.orderReference());
    }
}
