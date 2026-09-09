package com.orqentra.order.admin;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final DlqService dlq;
    private final StuckOrderDetector stuckOrders;

    public AdminController(DlqService dlq, StuckOrderDetector stuckOrders) {
        this.dlq = dlq;
        this.stuckOrders = stuckOrders;
    }

    @GetMapping("/dlq/topics")
    public List<DlqViews.DlqTopic> dlqTopics() {
        return dlq.topics();
    }

    @GetMapping("/dlq/{topic}")
    public List<DlqViews.DlqMessage> dlqMessages(@PathVariable String topic) {
        return dlq.messages(topic);
    }

    /**
     * Republishes a dead letter topic back to the topic it came from.
     *
     * <p>Safe because every listener is idempotent: a replayed event that was in fact
     * already processed is recognised by its eventId and skipped, so replaying a topic
     * cannot double-apply work that already happened.
     */
    @PostMapping("/dlq/{topic}/replay")
    public DlqViews.ReplayResult replay(@PathVariable String topic) {
        return dlq.replay(topic);
    }

    @GetMapping("/stuck-orders")
    public List<StuckOrderView> stuckOrders() {
        return stuckOrders.stuckOrders();
    }
}
