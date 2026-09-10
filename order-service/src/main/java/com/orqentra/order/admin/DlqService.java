package com.orqentra.order.admin;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;

@Service
public class DlqService {

    private static final Logger log = LoggerFactory.getLogger(DlqService.class);

    private static final String DLQ_SUFFIX = ".dlq";
    private static final Duration POLL = Duration.ofSeconds(2);

    private final String bootstrapServers;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public DlqService(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                      KafkaTemplate<String, byte[]> outboxKafkaTemplate,
                      ObjectProvider<KafkaConnectionDetails> connectionDetails) {
        // The property is the fallback. A KafkaConnectionDetails bean — from a
        // @ServiceConnection container in tests, or a docker-compose-derived connection
        // in dev — takes priority, the same way the auto-configured consumer and
        // producer factories already prefer it over the plain property.
        this.bootstrapServers = connectionDetails.stream()
                .findFirst()
                .map(details -> String.join(",", details.getBootstrapServers()))
                .orElse(bootstrapServers);
        this.kafkaTemplate = outboxKafkaTemplate;
    }

    public List<DlqViews.DlqTopic> topics() {
        Set<String> names;
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            names = admin.listTopics().names().get().stream()
                    .filter(name -> name.endsWith(DLQ_SUFFIX))
                    .collect(Collectors.toCollection(java.util.TreeSet::new));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while listing dead letter topics", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Could not list dead letter topics", ex);
        }

        List<DlqViews.DlqTopic> result = new ArrayList<>();
        try (Consumer<String, byte[]> consumer = consumer()) {
            for (String name : names) {
                List<TopicPartition> partitions = partitionsOf(consumer, name);
                Map<TopicPartition, Long> beginning = consumer.beginningOffsets(partitions);
                Map<TopicPartition, Long> end = consumer.endOffsets(partitions);
                long count = partitions.stream()
                        .mapToLong(tp -> end.getOrDefault(tp, 0L) - beginning.getOrDefault(tp, 0L))
                        .sum();
                result.add(new DlqViews.DlqTopic(name, count));
            }
        }
        return result;
    }

    public List<DlqViews.DlqMessage> messages(String topic) {
        List<DlqViews.DlqMessage> messages = new ArrayList<>();

        try (Consumer<String, byte[]> consumer = consumer()) {
            List<TopicPartition> partitions = partitionsOf(consumer, topic);
            if (partitions.isEmpty()) {
                return messages;
            }
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);

            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);
            while (!drained(consumer, partitions, end)) {
                ConsumerRecords<String, byte[]> records = consumer.poll(POLL);
                if (records.isEmpty()) {
                    break;
                }
                for (ConsumerRecord<String, byte[]> record : records) {
                    messages.add(toMessage(record));
                }
            }
        }
        return messages;
    }

    /**
     * Republishes every dead letter message back to the topic it failed on.
     *
     * <p>This is only safe because consumers are idempotent: each event carries an eventId
     * that the receiving listener claims in the same transaction as its work, so a replayed
     * event that did in fact succeed the first time is recognised and skipped rather than
     * applied twice. Without Part 1 this endpoint would be a way to double-charge or
     * double-release stock.
     */
    public DlqViews.ReplayResult replay(String topic) {
        String original = topic.endsWith(DLQ_SUFFIX)
                ? topic.substring(0, topic.length() - DLQ_SUFFIX.length())
                : topic;

        List<DlqViews.DlqMessage> pending = messages(topic);

        try (Consumer<String, byte[]> consumer = consumer()) {
            List<TopicPartition> partitions = partitionsOf(consumer, topic);
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);

            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);
            int replayed = 0;

            while (!drained(consumer, partitions, end)) {
                ConsumerRecords<String, byte[]> records = consumer.poll(POLL);
                if (records.isEmpty()) {
                    break;
                }
                for (ConsumerRecord<String, byte[]> record : records) {
                    ProducerRecord<String, byte[]> out =
                            new ProducerRecord<>(original, record.key(), record.value());

                    // Carry the type header across, or the consumer cannot resolve the record.
                    Header typeId = record.headers().lastHeader("__TypeId__");
                    if (typeId != null) {
                        out.headers().add("__TypeId__", typeId.value());
                    }

                    kafkaTemplate.send(out);
                    replayed++;
                }
            }

            kafkaTemplate.flush();
            log.info("Replayed {} message(s) from {} to {}", replayed, topic, original);
            return new DlqViews.ReplayResult(topic, original, replayed);
        } finally {
            pending.clear();
        }
    }

    private DlqViews.DlqMessage toMessage(ConsumerRecord<String, byte[]> record) {
        return new DlqViews.DlqMessage(
                record.partition(),
                record.offset(),
                record.key(),
                record.value() == null ? null : new String(record.value(), StandardCharsets.UTF_8),
                header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC),
                header(record, KafkaHeaders.DLT_EXCEPTION_FQCN),
                header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE));
    }

    private String header(ConsumerRecord<String, byte[]> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private boolean drained(Consumer<String, byte[]> consumer,
                            List<TopicPartition> partitions,
                            Map<TopicPartition, Long> end) {
        for (TopicPartition partition : partitions) {
            if (consumer.position(partition) < end.getOrDefault(partition, 0L)) {
                return false;
            }
        }
        return true;
    }

    private List<TopicPartition> partitionsOf(Consumer<String, byte[]> consumer, String topic) {
        List<PartitionInfo> info = consumer.partitionsFor(topic);
        if (info == null) {
            return List.of();
        }
        return info.stream()
                .map(p -> new TopicPartition(p.topic(), p.partition()))
                .toList();
    }

    /**
     * A throwaway consumer with raw byte deserializers: the whole point is to read records
     * that the configured deserializer could not handle.
     */
    private Consumer<String, byte[]> consumer() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "order-service-dlq-admin");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(config);
    }
}
