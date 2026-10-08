package com.trishla.copilot.model;

import java.time.Instant;

/** Wire format of a log event on the app-logs Kafka topic. */
public record LogEvent(
        String service,
        String level,
        String exceptionType,
        String message,
        String traceId,
        Instant timestamp) {
}
