package com.trishla.copilot.ai;

import java.util.List;

/** A resolved incident write-up used as retrieval context for the advisor. */
public record PastIncident(String id, String title, String service, String symptoms,
                           String rootCause, String fix, List<String> keywords) {
}
