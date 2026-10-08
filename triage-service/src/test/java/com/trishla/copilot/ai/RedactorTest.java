package com.trishla.copilot.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedactorTest {

    @Test
    void removesSecretsAndPersonalData() {
        String raw = "login failed for jane@example.com password=hunter2 "
                + "Authorization: Bearer abc.def.ghi card 4111111111111111";
        String out = Redactor.redact(raw);
        assertThat(out).doesNotContain("jane@example.com", "hunter2", "abc.def.ghi", "4111111111111111");
        assertThat(out).contains("[REDACTED]");
    }

    @Test
    void leavesNormalTextAlone() {
        assertThat(Redactor.redact("Query timed out after 30000 ms"))
                .isEqualTo("Query timed out after 30000 ms");
    }
}
