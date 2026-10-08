package com.trishla.copilot.api;

import com.trishla.copilot.repo.IncidentRepository;
import com.trishla.copilot.triage.TriageStats;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final TriageStats stats;
    private final IncidentRepository incidents;

    public StatsController(TriageStats stats, IncidentRepository incidents) {
        this.stats = stats;
        this.incidents = incidents;
    }

    @GetMapping
    public Map<String, Object> get() {
        return Map.of(
                "eventsProcessed", stats.processed(),
                "eventsPerSecond", Math.round(stats.throughputPerSecond() * 10) / 10.0,
                "avgProcessingMs", Math.round(stats.avgProcessingMillis() * 100) / 100.0,
                "activeIncidents", incidents.countByStatusNot("RESOLVED"));
    }

    /** Reset counters before a load test. */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        stats.reset();
        return get();
    }
}
