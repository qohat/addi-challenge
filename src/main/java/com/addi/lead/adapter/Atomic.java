package com.addi.lead.adapter;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Temp file then rename, so a reader never sees half a file. Both files this project writes are read
 * whole and rewritten whole, which is what makes one rename enough.
 *
 * <p>The failure is raised, not swallowed. What losing the write costs differs per caller and only
 * the caller knows it, so the decision belongs there (ADR 0003).
 */
final class Atomic {

    static void write(Path file, List<String> lines) throws IOException {
        var temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.write(temp, lines);
            Files.move(temp, file, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(temp);
            throw e;
        }
    }

    /** A half-written temp file is not data and not something a reviewer should find. */
    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // The write is already failing and its exception is the one worth having.
        }
    }

    private Atomic() {}
}
