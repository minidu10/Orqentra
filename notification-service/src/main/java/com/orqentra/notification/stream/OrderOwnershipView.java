package com.orqentra.notification.stream;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * A read model: the smallest projection this service needs, built from the events it
 * already consumes.
 *
 * <p>Only order.created carries the restaurant reference. Every later event in the saga
 * carries just the order reference, so without this map the service could not tell which
 * client should receive them. The alternative — asking the order service over HTTP on
 * every event — would put a synchronous dependency back on the path and undo the
 * decoupling the rest of the architecture exists to provide.
 *
 * <p>The cost is that this map lives only in memory: see restaurantFor.
 */
@Component
public class OrderOwnershipView {

    private final Map<String, String> ownerByOrder = new ConcurrentHashMap<>();

    public void record(String orderReference, String restaurantRef) {
        ownerByOrder.put(orderReference, restaurantRef);
    }

    /**
     * Empty when the mapping is unknown, which happens for any order created before the
     * last restart of this service. The caller must drop the event rather than fail:
     * throwing would have the container retry a message that can never become routable,
     * blocking the partition for every order behind it.
     */
    public Optional<String> restaurantFor(String orderReference) {
        return Optional.ofNullable(ownerByOrder.get(orderReference));
    }

    public int size() {
        return ownerByOrder.size();
    }
}
