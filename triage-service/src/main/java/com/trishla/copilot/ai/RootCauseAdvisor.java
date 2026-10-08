package com.trishla.copilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trishla.copilot.model.Incident;
import com.trishla.copilot.model.LogEventEntity;
import com.trishla.copilot.repo.IncidentRepository;
import com.trishla.copilot.repo.LogEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Suggests a likely root cause and fix for an incident using retrieval over past incidents
 * plus an LLM. Guardrails: logs are redacted before leaving the service, the advisor only
 * suggests (never acts), and it declines when retrieval finds no strong evidence.
 */
@Service
public class RootCauseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(RootCauseAdvisor.class);
    private static final double MIN_EVIDENCE = 0.12;

    private static final String SYSTEM_PROMPT = """
            You are an on-call SRE assistant. Using ONLY the incident data and the past incidents
            provided, suggest the most likely root cause and a concrete next step.
            Reply with JSON only, no prose, in exactly this shape:
            {"rootCause": "...", "fix": "...", "confidence": "high|medium|low", "citations": ["PI-..."]}
            Cite only past incident ids that actually support your answer. If the evidence is weak,
            say so in rootCause and set confidence to "low".""";

    private final IncidentRepository incidents;
    private final LogEventRepository events;
    private final KnowledgeBase kb;
    private final LlmClient llm;
    private final ObjectMapper mapper;

    public RootCauseAdvisor(IncidentRepository incidents, LogEventRepository events,
                            KnowledgeBase kb, LlmClient llm, ObjectMapper mapper) {
        this.incidents = incidents;
        this.events = events;
        this.kb = kb;
        this.llm = llm;
        this.mapper = mapper;
    }

    @Async
    public void analyzeAsync(Long incidentId) {
        try {
            analyze(incidentId);
        } catch (Exception e) {
            log.warn("Analysis failed for incident {}: {}", incidentId, e.getMessage());
        }
    }

    public Incident analyze(Long incidentId) {
        Incident incident = incidents.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("No incident " + incidentId));
        List<LogEventEntity> recent = events.findTop10ByIncidentIdOrderByTimestampDesc(incidentId);

        String query = String.join(" ", incident.getService(), incident.getExceptionType(),
                incident.getMessageTemplate());
        List<KnowledgeBase.Match> matches = kb.search(incident.getService(), query, 3);

        if (matches.isEmpty() || matches.get(0).score() < MIN_EVIDENCE) {
            return save(incidentId, "Insufficient evidence: no similar past incident found.",
                    "Investigate manually and add a write-up for this incident to the knowledge base.",
                    "low", List.of(), "none");
        }

        String reply = llm.complete(SYSTEM_PROMPT, buildPrompt(incident, recent, matches));
        if (reply != null) {
            Incident fromLlm = saveLlmReply(incidentId, reply, matches);
            if (fromLlm != null) {
                return fromLlm;
            }
        }

        // Retrieval-only fallback: reuse the closest past incident's diagnosis.
        PastIncident best = matches.get(0).incident();
        return save(incidentId, best.rootCause(), best.fix(),
                matches.get(0).score() > 0.35 ? "medium" : "low", List.of(best.id()), "retrieval");
    }

    private String buildPrompt(Incident incident, List<LogEventEntity> recent,
                               List<KnowledgeBase.Match> matches) {
        StringBuilder sb = new StringBuilder();
        sb.append("INCIDENT\n")
          .append("service: ").append(incident.getService()).append('\n')
          .append("exception: ").append(incident.getExceptionType()).append('\n')
          .append("template: ").append(Redactor.redact(incident.getMessageTemplate())).append('\n')
          .append("events so far: ").append(incident.getEventCount()).append('\n')
          .append("recent log lines:\n");
        for (LogEventEntity e : recent) {
            sb.append("- [").append(e.getLevel()).append("] ")
              .append(Redactor.redact(e.getMessage())).append('\n');
        }
        sb.append("\nPAST INCIDENTS\n");
        for (KnowledgeBase.Match m : matches) {
            PastIncident p = m.incident();
            sb.append(p.id()).append(" | ").append(p.title()).append('\n')
              .append("  symptoms: ").append(p.symptoms()).append('\n')
              .append("  root cause: ").append(p.rootCause()).append('\n')
              .append("  fix: ").append(p.fix()).append('\n');
        }
        return sb.toString();
    }

    private Incident saveLlmReply(Long incidentId, String reply, List<KnowledgeBase.Match> matches) {
        try {
            int start = reply.indexOf('{');
            int end = reply.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            JsonNode node = mapper.readTree(reply.substring(start, end + 1));
            Set<String> allowed = matches.stream().map(m -> m.incident().id()).collect(Collectors.toSet());
            List<String> cites = new ArrayList<>();
            node.path("citations").forEach(c -> {
                if (allowed.contains(c.asText())) {   // drop hallucinated citations
                    cites.add(c.asText());
                }
            });
            return save(incidentId, node.path("rootCause").asText(), node.path("fix").asText(),
                    node.path("confidence").asText("low"), cites, "llm");
        } catch (Exception e) {
            log.warn("Could not parse LLM reply: {}", e.getMessage());
            return null;
        }
    }

    private Incident save(Long incidentId, String rootCause, String fix, String confidence,
                          List<String> citations, String source) {
        incidents.updateAi(incidentId, truncate(rootCause), truncate(fix), confidence,
                String.join(",", citations), source, Instant.now());
        return incidents.findById(incidentId).orElseThrow();
    }

    private static String truncate(String s) {
        return s == null ? null : (s.length() > 1900 ? s.substring(0, 1900) : s);
    }
}
