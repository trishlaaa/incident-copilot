package com.trishla.copilot.ai;

import java.util.List;
import java.util.regex.Pattern;

/** Removes secrets and personal data from log text before it is sent to an LLM. */
public final class Redactor {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("(?i)bearer\\s+[A-Za-z0-9\\-._~+/]+=*"),
            Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"),        // JWTs
            Pattern.compile("(?i)(password|passwd|pwd|secret|api[_-]?key|token)\\s*[=:]\\s*\\S+"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),                                          // AWS access keys
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),                  // emails
            Pattern.compile("\\b\\d{12,19}\\b")                                                  // card-like numbers
    );

    private Redactor() { }

    public static String redact(String text) {
        if (text == null) {
            return null;
        }
        String out = text;
        for (Pattern p : PATTERNS) {
            out = p.matcher(out).replaceAll("[REDACTED]");
        }
        return out;
    }
}
