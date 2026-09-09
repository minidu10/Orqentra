package com.orqentra.order.admin;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.orqentra.order.ordering.OrderRepository;
import com.orqentra.order.ordering.OrderStatus;

/**
 * A saga has no owner once its event is lost, so nothing else would ever notice an order
 * left in a non-terminal state. This only reports them: what to do about a stuck order is
 * a business decision, not something to automate behind the operator's back.
 */
@Component
public class StuckOrderDetector {

    private static final Logger log = LoggerFactory.getLogger(StuckOrderDetector.class);

    private static final List<OrderStatus> NON_TERMINAL =
            List.of(OrderStatus.PENDING, OrderStatus.AWAITING_PAYMENT);

    private final OrderRepository orders;
    private final Duration stuckAfter;

    public StuckOrderDetector(OrderRepository orders,
                              @Value("${orders.stuck-after}") Duration stuckAfter) {
        this.orders = orders;
        this.stuckAfter = stuckAfter;
    }

    @Transactional(readOnly = true)
    public List<StuckOrderView> stuckOrders() {
        Instant now = Instant.now();
        return orders.findByStatusInAndCreatedAtBefore(NON_TERMINAL, now.minus(stuckAfter)).stream()
                .map(order -> StuckOrderView.from(order, now))
                .toList();
    }

    @Scheduled(fixedDelay = 30000)
    public void reportStuckOrders() {
        List<StuckOrderView> stuck = stuckOrders();
        if (stuck.isEmpty()) {
            return;
        }

        log.warn("{} order(s) stuck in a non-terminal state for more than {}", stuck.size(), stuckAfter);
        for (StuckOrderView order : stuck) {
            log.warn("Stuck order {} is {} and {}s old", order.reference(), order.status(), order.ageSeconds());
        }
    }
}
