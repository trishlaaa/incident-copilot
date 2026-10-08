package com.trishla.copilot.simulate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulate")
public class SimulationController {

    private final LogSimulator simulator;

    public SimulationController(LogSimulator simulator) {
        this.simulator = simulator;
    }

    @GetMapping("/scenarios")
    public List<Scenario> scenarios() {
        return LogSimulator.SCENARIOS;
    }

    /** Inject one failure scenario, e.g. POST /api/simulate/db-timeout?count=25 */
    @PostMapping("/{scenarioId}")
    public ResponseEntity<Map<String, Object>> inject(@PathVariable String scenarioId,
                                                      @RequestParam(defaultValue = "20") int count) {
        return simulator.find(scenarioId)
                .map(s -> ResponseEntity.ok(Map.<String, Object>of(
                        "scenario", s.id(), "sent", simulator.inject(s, Math.min(count, 5000)))))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Load test: POST /api/simulate/burst?count=50000 sends a random mix of scenarios. */
    @PostMapping("/burst")
    public Map<String, Object> burst(@RequestParam(defaultValue = "10000") int count) {
        return Map.of("sent", simulator.burst(Math.min(count, 200_000)));
    }
}
