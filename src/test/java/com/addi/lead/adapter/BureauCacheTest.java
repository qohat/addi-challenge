package com.addi.lead.adapter;

import static com.addi.lead.FixtureDir.ID;
import static com.addi.lead.FixtureDir.LEAD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.addi.lead.FixtureDir;
import com.addi.lead.domain.BureauOutcome;
import com.addi.lead.domain.Lead;
import com.addi.lead.port.ComplianceBureau;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The cache against a real file and a fixed clock. Nothing here waits for a TTL to pass. */
class BureauCacheTest {

    private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");
    private static final Duration TTL = Duration.ofHours(24);
    private static final String HEADER = "id,recordedAt,outcome,list";
    private static final String OTHER = "9999999999";

    @TempDir
    Path dir;

    private final List<Lead> calls = new ArrayList<>();

    /** The delegate a hit must not reach: an assertion is louder than a counter that nobody reads. */
    private record Exploding() implements ComplianceBureau {
        @Override
        public BureauOutcome screen(Lead lead) {
            throw new AssertionError("the bureau was called for " + lead.id().value());
        }
    }

    private record Counting(List<Lead> calls, BureauOutcome outcome) implements ComplianceBureau {
        @Override
        public BureauOutcome screen(Lead lead) {
            calls.add(lead);
            return outcome;
        }
    }

    private Path file() {
        return dir.resolve("data").resolve("bureau-cache.csv");
    }

    private CachingComplianceBureau cache(ComplianceBureau delegate) {
        return new CachingComplianceBureau(delegate, file(), TTL, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** An entry older than this run, which is the only way to test expiry without waiting a day. */
    private String cached(Instant recordedAt, String outcome, String list) {
        return cached(ID + "," + recordedAt + "," + outcome + "," + list);
    }

    private String cached(String... rows) {
        var content = HEADER + "\n" + String.join("\n", rows) + "\n";
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return content;
    }

    private String content() {
        try {
            return Files.readString(file());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> lines() {
        return List.of(content().strip().split("\n"));
    }

    private List<String> dataDirectory() {
        try (var entries = Files.list(dir.resolve("data"))) {
            return entries.map(path -> path.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void aSecondScreenInsideTheTtlCostsNoCallAndNoLatency() {
        var pauses = new ArrayList<Duration>();
        FixtureDir.write(dir, "bureau.csv", ID + ",,300,UP");
        var bureau = cache(new FixtureComplianceBureau(dir, pauses::add));

        assertEquals(new BureauOutcome.Clear(), bureau.screen(LEAD));
        assertEquals(new BureauOutcome.Clear(), bureau.screen(LEAD));

        assertEquals(List.of(Duration.ofMillis(300)), pauses, "the second screen paid the bureau's latency again");
    }

    @Test
    void theFirstScreenWritesOneRowStampedByTheClock() {
        cache(new Counting(calls, new BureauOutcome.Clear())).screen(LEAD);

        assertEquals(List.of(HEADER, ID + ",2026-08-17T12:00:00Z,CLEAR,"), lines());
    }

    @Test
    void aSanctionsHitIsCachedWithItsList() {
        cache(new Counting(calls, new BureauOutcome.Sanctioned("OFAC"))).screen(LEAD);

        assertEquals(new BureauOutcome.Sanctioned("OFAC"), cache(new Exploding()).screen(LEAD));
    }

    @Test
    void anUnavailableIsNeverWritten() {
        var bureau = cache(new Counting(calls, new BureauOutcome.Unavailable("the compliance bureau is down")));

        bureau.screen(LEAD);
        bureau.screen(LEAD);

        assertFalse(Files.exists(file()), "an outage was cached");
        assertEquals(2, calls.size());
    }

    @Test
    void anUnavailableAfterAnExpiredEntryLeavesTheRowAsItWas() {
        var written = cached(NOW.minus(TTL), "CLEAR", "");

        cache(new Counting(calls, new BureauOutcome.Unavailable("the compliance bureau is down"))).screen(LEAD);

        assertEquals(written, content());
        assertEquals(1, calls.size());
    }

    @Test
    void theTtlBoundaryIsExpiredAndOneMillisecondInsideItIsAHit() {
        cached(NOW.minus(TTL), "CLEAR", "");
        var fresh = new BureauOutcome.Sanctioned("OFAC");

        assertEquals(fresh, cache(new Counting(calls, fresh)).screen(LEAD));

        cached(NOW.minus(TTL).plusMillis(1), "CLEAR", "");
        assertEquals(new BureauOutcome.Clear(), cache(new Exploding()).screen(LEAD));
    }

    @Test
    void aFileWrittenByOneInstanceIsServedByAnother() {
        cache(new Counting(calls, new BureauOutcome.Clear())).screen(LEAD);

        assertEquals(new BureauOutcome.Clear(), cache(new Exploding()).screen(LEAD));
    }

    @Test
    void aRowThatDoesNotParseIsAMissAndIsDroppedByTheNextWrite() {
        var corrupt = new String[] {
            ID + ",garbage", ID + ",not-an-instant,CLEAR,", ID + ",2026-08-17T11:00:00Z,SIDEWAYS,"
        };

        for (var row : corrupt) {
            cached(row);
            var delegate = new Counting(new ArrayList<>(), new BureauOutcome.Clear());

            assertEquals(new BureauOutcome.Clear(), cache(delegate).screen(LEAD), row);
            assertEquals(1, delegate.calls().size(), row);
            assertEquals(List.of(HEADER, ID + ",2026-08-17T12:00:00Z,CLEAR,"), lines(), row);
        }
    }

    @Test
    void cachingOneLeadLeavesAnotherRowUntouched() {
        var other = OTHER + ",2026-08-16T12:00:00Z,SANCTIONED,OFAC";
        cached(other);

        cache(new Counting(calls, new BureauOutcome.Clear())).screen(LEAD);

        assertEquals(List.of(HEADER, other, ID + ",2026-08-17T12:00:00Z,CLEAR,"), lines());
    }

    @Test
    void theDataDirectoryIsCreatedAndHoldsOnlyTheCacheFile() {
        assertFalse(Files.exists(dir.resolve("data")));

        cache(new Counting(calls, new BureauOutcome.Clear())).screen(LEAD);

        assertEquals(List.of("bureau-cache.csv"), dataDirectory());
    }

    @Test
    void aCacheThatCannotBeReadOrWrittenStillScreens() throws IOException {
        Files.createDirectories(file());

        assertEquals(new BureauOutcome.Clear(), cache(new Counting(calls, new BureauOutcome.Clear())).screen(LEAD));
        assertEquals(List.of("bureau-cache.csv"), dataDirectory(), "a temp file was left behind");
    }
}
