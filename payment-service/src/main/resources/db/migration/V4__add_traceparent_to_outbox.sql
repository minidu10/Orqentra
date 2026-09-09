-- The publisher runs on a scheduler thread long after the business transaction ended,
-- so the originating trace has to travel with the row or the published event starts a
-- new, unconnected trace.
ALTER TABLE outbox_events ADD COLUMN traceparent VARCHAR(64);
