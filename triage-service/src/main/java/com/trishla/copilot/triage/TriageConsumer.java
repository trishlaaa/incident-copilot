package com.trishla.copilot.triage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trishla.copilot.model.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TriageConsumer {

    private static final Logger log = LoggerFactory.getLogger(TriageConsumer.class);

    private final ObjectMapper mapper;
    private final TriageService triage;
    private final TriageStats stats;

    public TriageConsumer(ObjectMapper mapper, TriageService triage, TriageStats stats) {
        this.mapper = mapper;
        this.triage = triage;
        this.stats = stats;
    }

    @KafkaListener(topics = "${app.topic}", groupId = "triage")
    public void onMessage(String payload) {
        long start = System.nanoTime();
        try {
            LogEvent event = mapper.readValue(payload, LogEvent.class);
            triage.process(event);
            stats.record(System.nanoTime() - start);
        } catch (Exception e) {
            // Skip malformed events instead of blocking the partition.
            log.warn("Skipping event that could not be processed: {}", e.getMessage());
        }
    }
}
