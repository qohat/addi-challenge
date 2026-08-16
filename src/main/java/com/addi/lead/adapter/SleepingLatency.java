package com.addi.lead.adapter;

import com.addi.lead.port.Latency;
import java.time.Duration;

/**
 * The simulated wait, and the only reason the scope timeout has anything to fire on. Interruption is
 * the scope cancelling the branch, so the flag is restored and the call returns: the orchestrator has
 * already stopped reading this branch's result.
 */
public record SleepingLatency() implements Latency {

    @Override
    public void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
