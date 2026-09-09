package com.orqentra.order.messaging;

import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
@EnableScheduling
public class MessagingConfig {

    /**
     * Sends the outbox payload as raw bytes. The JSON was already produced when the row
     * was written, so re-serialising it here would only risk changing it.
     */
    @Bean
    KafkaTemplate<String, byte[]> outboxKafkaTemplate(KafkaProperties properties,
                                                      ObservationRegistry observationRegistry) {
        Map<String, Object> config = properties.buildProducerProperties();
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        ProducerFactory<String, byte[]> factory = new DefaultKafkaProducerFactory<>(config);

        KafkaTemplate<String, byte[]> template = new KafkaTemplate<>(factory);

        // spring.kafka.template.observation-enabled only reaches the auto-configured
        // template. This one is built by hand, so it has to be switched on explicitly —
        // and without it there is no producer span, nothing writes a traceparent header
        // onto the message, and every consumer starts a brand new trace. The saga then
        // appears in Jaeger as a series of unrelated fragments.
        template.setObservationEnabled(true);
        template.setObservationRegistry(observationRegistry);
        return template;
    }

    /**
     * The dead letter template has to cope with two shapes of value. When the payload could
     * not be deserialised the record carries the original raw bytes; when the payload was
     * fine and the listener threw, it carries the deserialised event object. A serializer
     * that handles only one of them makes the dead letter publish itself fail, and the
     * record then retries forever instead of being parked.
     */
    @Bean
    KafkaTemplate<Object, Object> deadLetterKafkaTemplate(KafkaProperties properties) {
        JacksonJsonSerializer<Object> jsonSerializer = new JacksonJsonSerializer<>();
        Serializer<Object> valueSerializer = new Serializer<>() {
            @Override
            public byte[] serialize(String topic, Object data) {
                if (data == null) {
                    return null;
                }
                if (data instanceof byte[] bytes) {
                    return bytes;
                }
                return jsonSerializer.serialize(topic, data);
            }
        };

        StringSerializer stringSerializer = new StringSerializer();
        Serializer<Object> keySerializer = new Serializer<>() {
            @Override
            public byte[] serialize(String topic, Object data) {
                if (data == null) {
                    return null;
                }
                if (data instanceof byte[] bytes) {
                    return bytes;
                }
                return stringSerializer.serialize(topic, data.toString());
            }
        };

        Map<String, Object> config = properties.buildProducerProperties();
        config.remove(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG);
        config.remove(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG);

        ProducerFactory<Object, Object> factory =
                new DefaultKafkaProducerFactory<Object, Object>(config, keySerializer, valueSerializer);
        return new KafkaTemplate<>(factory);
    }

    /**
     * Three attempts with backoff starting at one second and doubling, then the record is
     * routed to &lt;topic&gt;.dlq instead of blocking its partition forever.
     *
     * <p>The two failure classes are kept apart deliberately. A payload that cannot be
     * deserialised, or one the listener rejects as invalid, will never succeed however
     * often it is retried, so it skips the backoff entirely and goes straight to the DLQ.
     * A transient fault such as a dropped database connection exhausts the retries first,
     * because those do succeed on a later attempt.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> deadLetterKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                deadLetterKafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".dlq", record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxAttempts(3);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(
                DeserializationException.class,
                MessageConversionException.class,
                IllegalArgumentException.class);
        return handler;
    }
}
