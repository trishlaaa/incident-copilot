package com.trishla.copilot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate   // only write changed columns, so triage and AI updates don't overwrite each other
@Table(name = "incidents", indexes = @Index(name = "idx_incident_fp", columnList = "fingerprint"))
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String service;
    private String exceptionType;
    private String fingerprint;

    @Column(length = 1000)
    private String messageTemplate;

    @Column(length = 2000)
    private String sampleMessage;

    private String severity = "LOW";      // LOW, MEDIUM, HIGH
    private String status = "OPEN";       // OPEN, ACKNOWLEDGED, RESOLVED
    private int eventCount;
    private Instant firstSeen;
    private Instant lastSeen;

    // AI suggestion
    @Column(length = 2000)
    private String aiRootCause;
    @Column(length = 2000)
    private String aiFix;
    private String aiConfidence;
    private String aiCitations;           // comma-separated past incident ids
    private String aiSource;              // llm | retrieval | none
    private Instant aiAnalyzedAt;
    private String feedback;              // up | down

    public Long getId() { return id; }
    public String getService() { return service; }
    public void setService(String service) { this.service = service; }
    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public String getMessageTemplate() { return messageTemplate; }
    public void setMessageTemplate(String messageTemplate) { this.messageTemplate = messageTemplate; }
    public String getSampleMessage() { return sampleMessage; }
    public void setSampleMessage(String sampleMessage) { this.sampleMessage = sampleMessage; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getEventCount() { return eventCount; }
    public void setEventCount(int eventCount) { this.eventCount = eventCount; }
    public Instant getFirstSeen() { return firstSeen; }
    public void setFirstSeen(Instant firstSeen) { this.firstSeen = firstSeen; }
    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
    public String getAiRootCause() { return aiRootCause; }
    public void setAiRootCause(String aiRootCause) { this.aiRootCause = aiRootCause; }
    public String getAiFix() { return aiFix; }
    public void setAiFix(String aiFix) { this.aiFix = aiFix; }
    public String getAiConfidence() { return aiConfidence; }
    public void setAiConfidence(String aiConfidence) { this.aiConfidence = aiConfidence; }
    public String getAiCitations() { return aiCitations; }
    public void setAiCitations(String aiCitations) { this.aiCitations = aiCitations; }
    public String getAiSource() { return aiSource; }
    public void setAiSource(String aiSource) { this.aiSource = aiSource; }
    public Instant getAiAnalyzedAt() { return aiAnalyzedAt; }
    public void setAiAnalyzedAt(Instant aiAnalyzedAt) { this.aiAnalyzedAt = aiAnalyzedAt; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
}
