package com.addi.lead.port;

import java.time.Duration;

/**
 * Simulated latency is an effect, so it enters here. Every adapter asks for exactly what its fixture
 * row states and a test asserts that without waiting for it (ADR 0006).
 */
public interface Latency {
    void pause(Duration duration);
}
