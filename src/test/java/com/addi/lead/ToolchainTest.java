package com.addi.lead;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.StructuredTaskScope;
import org.junit.jupiter.api.Test;

/**
 * The build itself is what spec 00 delivers, so these are its tests: the release the whole project
 * is pinned to, and the preview API that pin exists for.
 */
class ToolchainTest {

    @Test
    void runsOnJava25() {
        assertEquals(25, Runtime.version().feature());
    }

    @Test
    void structuredTaskScopeForksAndJoins() throws Exception {
        try (var scope = StructuredTaskScope.open()) {
            var registry = scope.fork(() -> "registry");
            var judicial = scope.fork(() -> "judicial");
            scope.join();
            assertEquals("registry", registry.get());
            assertEquals("judicial", judicial.get());
        }
    }
}
