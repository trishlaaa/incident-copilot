package com.trishla.copilot.simulate;

/** A failure pattern the simulator can inject. %d / %s placeholders get random values. */
public record Scenario(String id, String label, String service, String level,
                       String exceptionType, String messageTemplate) {
}
