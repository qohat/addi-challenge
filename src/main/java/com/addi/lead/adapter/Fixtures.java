package com.addi.lead.adapter;

import com.addi.lead.domain.NationalId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * A CSV reader for five files with a header row and no quoting. Splitting by hand rather than taking
 * a dependency: the format exists to be read by a reviewer, not to be general.
 *
 * <p>Nothing here is caught. Every caller is an adapter, and translating a failure into that step's
 * {@code Unavailable} is the adapter's job (ADR 0005).
 */
final class Fixtures {

    /** Trailing empty cells are kept, so a lead with no sanctions list still has the column. */
    static List<String[]> rows(Path directory, String file) throws IOException {
        try (var lines = Files.lines(directory.resolve(file))) {
            return lines.skip(1)
                    .filter(line -> !line.isBlank())
                    .map(line -> line.split(",", -1))
                    .toList();
        }
    }

    /** Empty means the system has nothing on this person, which is not the same as being down. */
    static Optional<String[]> row(Path directory, String file, NationalId id) throws IOException {
        return rows(directory, file).stream()
                .filter(cells -> cells[0].trim().equals(id.value()))
                .findFirst();
    }

    /** Anything that is neither {@code UP} nor {@code DOWN} is a malformed row, not a third state. */
    static boolean isDown(String status) {
        return switch (status.trim()) {
            case "UP" -> false;
            case "DOWN" -> true;
            default -> throw new IllegalArgumentException("Unknown status: " + status);
        };
    }

    /** The message a reviewer sees on the pending line, so the class name earns its place. */
    static String reason(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getClass().getSimpleName() + ": "
                        + e.getMessage();
    }

    private Fixtures() {}
}
