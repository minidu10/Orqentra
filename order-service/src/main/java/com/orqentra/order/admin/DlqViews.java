package com.orqentra.order.admin;

public final class DlqViews {

    public record DlqTopic(String topic, long messageCount) {}

    public record DlqMessage(
            int partition,
            long offset,
            String key,
            String payload,
            String originalTopic,
            String exceptionType,
            String failureReason) {}

    public record ReplayResult(String topic, String replayedTo, int replayed) {}

    private DlqViews() {}
}
