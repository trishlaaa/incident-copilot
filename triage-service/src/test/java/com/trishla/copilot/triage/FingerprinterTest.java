package com.trishla.copilot.triage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FingerprinterTest {

    @Test
    void sameProblemWithDifferentIdsGivesSameFingerprint() {
        String a = Fingerprinter.fingerprint("payments", "SQLTimeoutException",
                "Query timed out after 30000 ms on table payments (orderId=3f2a9c1b)");
        String b = Fingerprinter.fingerprint("payments", "SQLTimeoutException",
                "Query timed out after 512 ms on table payments (orderId=abcdefab)");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void differentServicesGiveDifferentFingerprints() {
        String msg = "Connection refused after 3 retries";
        assertThat(Fingerprinter.fingerprint("payments", "ConnectException", msg))
                .isNotEqualTo(Fingerprinter.fingerprint("orders", "ConnectException", msg));
    }

    @Test
    void templateReplacesVariablePartsOnly() {
        assertThat(Fingerprinter.template(
                "Request 8f14e45f-ceea-467a-9575-4e8d6a5a7f3b from 10.0.0.12 failed after 250 ms"))
                .isEqualTo("Request <uuid> from <ip> failed after <*> ms");
    }

    @Test
    void severityEscalatesWithVolumeAndFatalLevel() {
        assertThat(TriageService.severityFor("ERROR", 1)).isEqualTo("LOW");
        assertThat(TriageService.severityFor("ERROR", 10)).isEqualTo("MEDIUM");
        assertThat(TriageService.severityFor("ERROR", 50)).isEqualTo("HIGH");
        assertThat(TriageService.severityFor("FATAL", 1)).isEqualTo("HIGH");
    }
}
