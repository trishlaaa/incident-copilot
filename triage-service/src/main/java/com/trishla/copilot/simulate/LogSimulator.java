package com.trishla.copilot.simulate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trishla.copilot.model.LogEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Plays the role of several microservices emitting error logs to Kafka. */
@Service
public class LogSimulator {

    public static final List<Scenario> SCENARIOS = List.of(
            new Scenario("db-timeout", "Database timeout in payments", "payments", "ERROR",
                    "SQLTimeoutException", "Query timed out after %d ms on table payments (orderId=%s)"),
            new Scenario("null-card", "Null card in payment validation", "payments", "ERROR",
                    "NullPointerException", "Cannot invoke \"Card.getNetwork()\" because \"card\" is null (attempt=%d, orderId=%s)"),
            new Scenario("jwt-invalid", "Invalid tokens in auth", "auth", "WARN",
                    "InvalidTokenException", "JWT signature verification failed for user u-%d (session %s)"),
            new Scenario("inventory-503", "Inventory service unavailable", "orders", "ERROR",
                    "HttpServerErrorException", "503 Service Unavailable from inventory-service after %d ms for order %s"),
            new Scenario("smtp-refused", "SMTP connection refused", "notifications", "ERROR",
                    "ConnectException", "Connection refused: smtp.internal:587 after %d retries (job %s)"),
            new Scenario("export-oom", "Out of memory in order export", "orders", "FATAL",
                    "OutOfMemoryError", "Java heap space while building export batch %d (requestId=%s)"),
            new Scenario("kafka-lag", "Consumer lag on ledger", "ledger", "WARN",
                    "ConsumerLagException", "Consumer group ledger-writer lag is %d messages on partition %s")
    );

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public LogSimulator(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                        @Value("${app.topic}") String topic) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = topic;
    }

    public Optional<Scenario> find(String id) {
        return SCENARIOS.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    /** Sends `count` events for one scenario. */
    public int inject(Scenario s, int count) {
        for (int i = 0; i < count; i++) {
            send(buildEvent(s));
        }
        return count;
    }

    /** Sends a random mix of scenarios - used for load testing throughput. */
    public int burst(int count) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            send(buildEvent(SCENARIOS.get(r.nextInt(SCENARIOS.size()))));
        }
        return count;
    }

    private LogEvent buildEvent(Scenario s) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String shortId = UUID.randomUUID().toString().substring(0, 8);
        String message = String.format(s.messageTemplate(), r.nextInt(100, 99999), shortId);
        return new LogEvent(s.service(), s.level(), s.exceptionType(), message,
                UUID.randomUUID().toString(), Instant.now());
    }

    private void send(LogEvent event) {
        try {
            kafka.send(topic, event.service(), mapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize log event", e);
        }
    }
}
