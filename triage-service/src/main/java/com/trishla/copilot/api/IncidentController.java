package com.trishla.copilot.api;

import com.trishla.copilot.ai.KnowledgeBase;
import com.trishla.copilot.ai.PastIncident;
import com.trishla.copilot.ai.RootCauseAdvisor;
import com.trishla.copilot.model.Incident;
import com.trishla.copilot.model.LogEventEntity;
import com.trishla.copilot.repo.IncidentRepository;
import com.trishla.copilot.repo.LogEventRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    public record IncidentDetail(Incident incident, List<LogEventEntity> recentEvents,
                                 List<PastIncident> citedPastIncidents) { }

    private static final Set<String> STATUSES = Set.of("OPEN", "ACKNOWLEDGED", "RESOLVED");

    private final IncidentRepository incidents;
    private final LogEventRepository events;
    private final RootCauseAdvisor advisor;
    private final KnowledgeBase kb;

    public IncidentController(IncidentRepository incidents, LogEventRepository events,
                              RootCauseAdvisor advisor, KnowledgeBase kb) {
        this.incidents = incidents;
        this.events = events;
        this.advisor = advisor;
        this.kb = kb;
    }

    @GetMapping
    public List<Incident> list() {
        return incidents.findTop100ByOrderByLastSeenDesc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentDetail> detail(@PathVariable Long id) {
        return incidents.findById(id)
                .map(i -> ResponseEntity.ok(new IncidentDetail(
                        i,
                        events.findTop10ByIncidentIdOrderByTimestampDesc(id),
                        cited(i))))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Body: {"status": "ACKNOWLEDGED"} */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Incident> setStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "").toUpperCase();
        if (!STATUSES.contains(status)) {
            return ResponseEntity.badRequest().build();
        }
        return incidents.findById(id).map(i -> {
            i.setStatus(status);
            return ResponseEntity.ok(incidents.save(i));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Body: {"feedback": "up"} or {"feedback": "down"} */
    @PostMapping("/{id}/feedback")
    public ResponseEntity<Incident> feedback(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String fb = body.getOrDefault("feedback", "");
        if (!fb.equals("up") && !fb.equals("down")) {
            return ResponseEntity.badRequest().build();
        }
        return incidents.findById(id).map(i -> {
            i.setFeedback(fb);
            return ResponseEntity.ok(incidents.save(i));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Re-run the AI analysis synchronously. */
    @PostMapping("/{id}/analyze")
    public ResponseEntity<Incident> analyze(@PathVariable Long id) {
        if (!incidents.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(advisor.analyze(id));
    }

    private List<PastIncident> cited(Incident i) {
        if (i.getAiCitations() == null || i.getAiCitations().isBlank()) {
            return List.of();
        }
        return Arrays.stream(i.getAiCitations().split(","))
                .map(String::trim)
                .map(kb::byId)
                .flatMap(Optional::stream)
                .toList();
    }
}
