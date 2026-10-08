package com.trishla.copilot.triage;

import com.trishla.copilot.ai.RootCauseAdvisor;
import com.trishla.copilot.model.Incident;
import com.trishla.copilot.model.LogEvent;
import com.trishla.copilot.model.LogEventEntity;
import com.trishla.copilot.repo.IncidentRepository;
import com.trishla.copilot.repo.LogEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;

/** Groups incoming error events into incidents. */
@Service
public class TriageService {

    private final IncidentRepository incidents;
    private final LogEventRepository events;
    private final RootCauseAdvisor advisor;
    private final Duration groupingWindow;

    public TriageService(IncidentRepository incidents, LogEventRepository events,
                         RootCauseAdvisor advisor,
                         @Value("${app.grouping-window-minutes:15}") long windowMinutes) {
        this.incidents = incidents;
        this.events = events;
        this.advisor = advisor;
        this.groupingWindow = Duration.ofMinutes(windowMinutes);
    }

    /** Returns the incident the event was attached to. */
    @Transactional
    public Incident process(LogEvent event) {
        Instant ts = event.timestamp() != null ? event.timestamp() : Instant.now();
        String fp = Fingerprinter.fingerprint(event.service(), event.exceptionType(), event.message());

        Incident incident = incidents
                .findFirstByFingerprintAndStatusNotAndLastSeenAfterOrderByLastSeenDesc(
                        fp, "RESOLVED", ts.minus(groupingWindow))
                .orElse(null);

        boolean isNew = incident == null;
        if (isNew) {
            incident = new Incident();
            incident.setService(event.service());
            incident.setExceptionType(event.exceptionType());
            incident.setFingerprint(fp);
            incident.setMessageTemplate(Fingerprinter.template(event.message()));
            incident.setSampleMessage(event.message());
            incident.setFirstSeen(ts);
        }
        incident.setEventCount(incident.getEventCount() + 1);
        incident.setLastSeen(ts);
        incident.setSeverity(severityFor(event.level(), incident.getEventCount()));
        incident = incidents.save(incident);

        events.save(new LogEventEntity(incident, event));

        if (isNew) {
            Long id = incident.getId();
            // Analyse only after the incident is committed, so the async thread can read it.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    advisor.analyzeAsync(id);
                }
            });
        }
        return incident;
    }

    static String severityFor(String level, int count) {
        if ("FATAL".equalsIgnoreCase(level) || count >= 50) {
            return "HIGH";
        }
        if (count >= 10) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
