package com.orqentra.order.metrics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * Consumer lag per group and topic, and dead letter depth per topic.
 *
 * <p>Lag is the metric that matters most in an event-driven system. Every service can be
 * up, every HTTP call fast, every dashboard green, and orders can still be silently
 * minutes behind because one consumer stopped keeping pace. Lag is how that is visible
 * before a customer notices, and it is the difference between a real dashboard and a
 * JVM memory chart.
 *
 * <p>Scraped by one service on behalf of the cluster: the numbers come from the broker,
 * not from any single consumer, so collecting them six times would produce six copies of
 * the same series.
 */
@Component
public class KafkaLagMetrics {

    private static final Logger log = LoggerFactory.getLogger(KafkaLagMetrics.class);

    private static final List<String> GROUPS = List.of(
            "order-service", "inventory-service", "payment-service", "notification-service");

    private final String bootstrapServers;
    private final MeterRegistry registry;

    private final Map<String, AtomicLong> lagGauges = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> dlqGauges = new ConcurrentHashMap<>();

    public KafkaLagMetrics(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                           MeterRegistry registry) {
        this.bootstrapServers = bootstrapServers;
        this.registry = registry;
    }

    @Scheduled(fixedDelay = 10000, initialDelay = 5000)
    public void sample() {
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {

            for (String group : GROUPS) {
                Map<TopicPartition, OffsetAndMetadata> committed =
                        admin.listConsumerGroupOffsets(group)
                                .partitionsToOffsetAndMetadata().get();
                if (committed.isEmpty()) {
                    continue;
                }

                Map<TopicPartition, OffsetSpec> wanted = new HashMap<>();
                committed.keySet().forEach(tp -> wanted.put(tp, OffsetSpec.latest()));
                Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> ends =
                        admin.listOffsets(wanted).all().get();

                committed.forEach((partition, offset) -> {
                    var end = ends.get(partition);
                    if (end == null || offset == null) {
                        return;
                    }
                    long lag = Math.max(0, end.offset() - offset.offset());
                    gauge(lagGauges, "orqentra.kafka.consumer.lag",
                            Tags.of("group", group, "topic", partition.topic()), lag);
                });
            }

            // Dead letter depth: nothing consumes these topics, so the end offset is the
            // number of parked messages.
            Set<String> dlqTopics = admin.listTopics().names().get().stream()
                    .filter(name -> name.endsWith(".dlq"))
                    .collect(java.util.stream.Collectors.toSet());

            for (String topic : dlqTopics) {
                var description = admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic);
                Map<TopicPartition, OffsetSpec> latest = new HashMap<>();
                Map<TopicPartition, OffsetSpec> earliest = new HashMap<>();
                description.partitions().forEach(p -> {
                    TopicPartition tp = new TopicPartition(topic, p.partition());
                    latest.put(tp, OffsetSpec.latest());
                    earliest.put(tp, OffsetSpec.earliest());
                });

                var ends = admin.listOffsets(latest).all().get();
                var starts = admin.listOffsets(earliest).all().get();
                long count = ends.entrySet().stream()
                        .mapToLong(e -> e.getValue().offset()
                                - starts.get(e.getKey()).offset())
                        .sum();

                gauge(dlqGauges, "orqentra.dlq.messages", Tags.of("topic", topic), count);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            // Losing a sample is not worth failing anything over.
            log.debug("Could not sample Kafka metrics: {}", ex.toString());
        }
    }

    private void gauge(Map<String, AtomicLong> holder, String name, Tags tags, long value) {
        holder.computeIfAbsent(name + tags, key -> {
            AtomicLong holderValue = new AtomicLong();
            registry.gauge(name, tags, holderValue);
            return holderValue;
        }).set(value);
    }
}
