package com.orqentra.order.ordering;

/**
 * Raised when an order is asked to move to a state its current state cannot reach — a
 * cancelled order confirmed, or a confirmed order cancelled. The saga only ever drives
 * transitions forward, so seeing this means either a redelivered event arrived after the
 * order already reached a terminal state, or a genuine bug in the orchestration.
 */
public class InvalidOrderTransitionException extends IllegalStateException {

    public InvalidOrderTransitionException(OrderStatus from, OrderStatus to) {
        super("Cannot move order from " + from + " to " + to);
    }
}
