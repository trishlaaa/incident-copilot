package com.trishla.copilot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "log_events", indexes = @Index(name = "idx_event_incident", columnList = "incident_id"))
public class LogEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    private String service;
    private String level;
    private String exceptionType;
    @Column(length = 2000)
    private String message;
    private String traceId;
    private Instant timestamp;

    public LogEventEntity() { }

    public LogEventEntity(Incident incident, LogEvent e) {
        this.incident = incident;
        this.service = e.service();
        this.level = e.level();
        this.exceptionType = e.exceptionType();
        this.message = e.message();
        this.traceId = e.traceId();
        this.timestamp = e.timestamp();
    }

    public Long getId() { return id; }
    public Incident getIncident() { return incident; }
    public String getService() { return service; }
    public String getLevel() { return level; }
    public String getExceptionType() { return exceptionType; }
    public String getMessage() { return message; }
    public String getTraceId() { return traceId; }
    public Instant getTimestamp() { return timestamp; }
}
