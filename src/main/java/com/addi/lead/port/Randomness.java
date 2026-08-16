package com.addi.lead.port;

/**
 * The score stays genuinely random in production; determinism is obtained by injecting a seed here,
 * never by removing the variance the brief asked for (ADR 0006).
 */
public interface Randomness {
    int nextInt(int bound);
}
