package com.orqentra.inventory.correlation;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

/**
 * Restores the correlation id from the Kafka header before a listener runs, so the async
 * half of a saga logs under the same id as the HTTP call that started it. Without this
 * only the initial request could be traced, and everything the event triggered afterwards
 * would be unattributable.
 */
@Component
public class RequestIdRecordInterceptor implements RecordInterceptor<Object, Object> {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record,
                                                    Consumer<Object, Object> consumer) {
        Header header = record.headers().lastHeader(HEADER);
        if (header != null) {
            MDC.put(MDC_KEY, new String(header.value(), StandardCharsets.UTF_8));
        }
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        MDC.remove(MDC_KEY);
    }
}
