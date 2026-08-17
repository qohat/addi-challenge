package com.addi.lead.app;

import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalLong;

/**
 * Configurable means injectable. None of these is a CLI flag: tests and the demo are the only
 * callers that need to change them, and a flag with no user is a flag to maintain for nobody.
 *
 * <p>An empty seed is a fresh one per process, which is what "random" means.
 */
public record Config(Duration parallelTimeout, Path fixtures, OptionalLong seed) {

    public static Config defaults() {
        return new Config(Duration.ofSeconds(2), Path.of("fixtures"), OptionalLong.empty());
    }
}
