package com.addi.lead.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.addi.lead.FixtureDir;
import com.addi.lead.domain.CaseId;
import com.addi.lead.domain.CaseStatus;
import com.addi.lead.domain.Checkpoint;
import com.addi.lead.domain.ReviewCase;
import com.addi.lead.domain.Step;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The queue against a temp directory, with the files read back as an analyst would find them. */
class ReviewQueueTest {

    private static final Instant NOW = Instant.parse("2026-08-16T10:15:30Z");

    private static final Checkpoint CHECKPOINT = new Checkpoint(FixtureDir.LEAD, Step.BUREAU, "connection refused");

    @TempDir
    Path dir;

    @Test
    void openCreatesTheDirectoryAndWritesOneFileNamedByTheCaseId() throws IOException {
        var queue = new FileReviewQueue(dir.resolve("review"));

        var id = queue.open(CHECKPOINT, NOW);

        assertEquals(FixtureDir.ID + "-" + NOW.toEpochMilli(), id.value());
        assertEquals(List.of(id.value()), names(dir.resolve("review")));
    }

    @Test
    void aCaseSurvivesTheRoundTripIncludingAReasonWithCommas() throws IOException {
        var checkpoint = new Checkpoint(FixtureDir.LEAD, Step.REGISTRY, "timeout after 100ms, both branches cancelled");
        var queue = new FileReviewQueue(dir);

        var id = queue.open(checkpoint, NOW);

        assertEquals(Optional.of(new ReviewCase(id, NOW, checkpoint, CaseStatus.OPEN)), queue.find(id));
    }

    @Test
    void twoCasesForOneLeadAreTwoFilesAndResolvingOneTouchesNothingElse() throws IOException {
        var queue = new FileReviewQueue(dir);
        var first = queue.open(CHECKPOINT, NOW);
        var second = queue.open(CHECKPOINT, NOW.plusMillis(1));

        queue.resolve(queue.find(first).orElseThrow(), CaseStatus.APPROVED);

        assertNotEquals(first, second);
        assertEquals(2, names(dir).size(), "resolve wrote a new file instead of replacing one");
        assertEquals(Optional.of(new ReviewCase(first, NOW, CHECKPOINT, CaseStatus.APPROVED)), queue.find(first));
        assertEquals(
                Optional.of(new ReviewCase(second, NOW.plusMillis(1), CHECKPOINT, CaseStatus.OPEN)),
                queue.find(second));
    }

    @Test
    void anUnknownIdAMissingKeyABadInstantAndAnUnknownStatusAreEachNoCase() throws IOException {
        var queue = new FileReviewQueue(dir);
        var id = queue.open(CHECKPOINT, NOW);
        var written = Files.readAllLines(dir.resolve(id.value()));

        assertEquals(Optional.empty(), queue.find(new CaseId("no-such-case")));
        assertEquals(Optional.empty(), findAfterRewriting(queue, id, dropping(written, "pending=")));
        assertEquals(Optional.empty(), findAfterRewriting(queue, id, replacing(written, "openedAt=", "yesterday")));
        assertEquals(Optional.empty(), findAfterRewriting(queue, id, replacing(written, "status=", "MAYBE")));
    }

    private Optional<ReviewCase> findAfterRewriting(FileReviewQueue queue, CaseId id, List<String> lines)
            throws IOException {
        Files.write(dir.resolve(id.value()), lines);
        return queue.find(id);
    }

    private static List<String> dropping(List<String> lines, String key) {
        return lines.stream().filter(line -> !line.startsWith(key)).toList();
    }

    private static List<String> replacing(List<String> lines, String key, String value) {
        return lines.stream().map(line -> line.startsWith(key) ? key + value : line).toList();
    }

    private static List<String> names(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }
}
