package com.trishla.copilot.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Small in-memory library of past incidents, with keyword-overlap retrieval.
 * (Next step: replace scoring with pgvector embeddings.)
 */
@Component
public class KnowledgeBase {

    public record Match(PastIncident incident, double score) { }

    private static final Set<String> STOP = Set.of(
            "the", "a", "an", "and", "or", "for", "of", "on", "in", "to", "is", "at", "by",
            "from", "with", "after", "while", "because", "be", "was");

    private final List<PastIncident> incidents;
    private final Map<String, Set<String>> tokensById = new HashMap<>();

    public KnowledgeBase(ObjectMapper mapper) throws IOException {
        try (InputStream in = new ClassPathResource("past-incidents.json").getInputStream()) {
            this.incidents = mapper.readValue(in, new TypeReference<List<PastIncident>>() { });
        }
        for (PastIncident p : incidents) {
            String text = String.join(" ", p.title(), p.symptoms(), String.join(" ", p.keywords()));
            tokensById.put(p.id(), tokenize(text));
        }
    }

    public List<PastIncident> all() {
        return incidents;
    }

    public Optional<PastIncident> byId(String id) {
        return incidents.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /** Top-k past incidents by token overlap, with a small bonus for the same service. */
    public List<Match> search(String service, String text, int k) {
        Set<String> query = tokenize(text);
        if (query.isEmpty()) {
            return List.of();
        }
        List<Match> scored = new ArrayList<>();
        for (PastIncident p : incidents) {
            Set<String> doc = tokensById.get(p.id());
            long overlap = query.stream().filter(doc::contains).count();
            if (overlap == 0) {
                continue;
            }
            double score = overlap / Math.sqrt((double) query.size() * doc.size());
            if (service != null && service.equalsIgnoreCase(p.service())) {
                score += 0.1;
            }
            scored.add(new Match(p, score));
        }
        scored.sort(Comparator.comparingDouble(Match::score).reversed());
        return scored.stream().limit(k).collect(Collectors.toList());
    }

    static Set<String> tokenize(String text) {
        if (text == null) {
            return Set.of();
        }
        // split camelCase (SQLTimeoutException -> sql timeout exception) and punctuation
        String spaced = text.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return Arrays.stream(spaced.split("[^a-z0-9]+"))
                .filter(t -> t.length() > 2 && !STOP.contains(t) && !t.chars().allMatch(Character::isDigit))
                .collect(Collectors.toSet());
    }
}
