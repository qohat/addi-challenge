package com.addi.lead.adapter;

import com.addi.lead.domain.BureauOutcome;
import com.addi.lead.domain.Lead;
import com.addi.lead.domain.NationalId;
import com.addi.lead.port.ComplianceBureau;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The bureau with its answers remembered on disk (ADR 0003). A decorator rather than a port of its
 * own: the cache is not a fifth external system and nothing above the composition root can tell that
 * an answer came from a file.
 *
 * <p>Nothing here throws. A cache that cannot be read is a miss and a cache that cannot be written
 * is a miss next time, and neither is a reason to fail a screening.
 */
public record CachingComplianceBureau(ComplianceBureau delegate, Path file, Duration ttl, Clock clock)
        implements ComplianceBureau {

    private static final String HEADER = "id,recordedAt,outcome,list";

    @Override
    public BureauOutcome screen(Lead lead) {
        return cached(lead.id()).orElseGet(() -> screenAndRemember(lead));
    }

    private BureauOutcome screenAndRemember(Lead lead) {
        var outcome = delegate.screen(lead);
        if (terminal(outcome)) {
            write(new Entry(lead.id().value(), clock.instant(), outcome));
        }
        return outcome;
    }

    /** An outage is not an answer: caching one turns a transient failure into a permanent one (ADR 0003). */
    private static boolean terminal(BureauOutcome outcome) {
        return switch (outcome) {
            case BureauOutcome.Clear ignored -> true;
            case BureauOutcome.Sanctioned ignored -> true;
            case BureauOutcome.Unavailable ignored -> false;
        };
    }

    /** The boundary is expired: an entry recorded exactly one TTL ago has no life left in it. */
    private Optional<BureauOutcome> cached(NationalId id) {
        var now = clock.instant();
        return entries()
                .filter(entry -> entry.id().equals(id.value()))
                .filter(entry -> now.isBefore(entry.recordedAt().plus(ttl)))
                .map(Entry::outcome)
                .findFirst();
    }

    /** Read whole, which ADR 0003 accepts at this scale. A row that does not parse is not an entry. */
    private Stream<Entry> entries() {
        try {
            return Files.readAllLines(file).stream().skip(1).map(Entry::parse).flatMap(Optional::stream);
        } catch (Exception e) {
            return Stream.empty();
        }
    }

    /**
     * The whole file every time, which is also the only pruning there is: rows that no longer parse do
     * not survive the rewrite. A failure is dropped here rather than raised, because the screening has
     * already decided and a lost entry costs one repeat call (ADR 0003).
     */
    private void write(Entry entry) {
        var kept = entries().filter(other -> !other.id().equals(entry.id()));
        var body = Stream.concat(kept, Stream.of(entry)).map(Entry::line);
        try {
            Atomic.write(file, Stream.concat(Stream.of(HEADER), body).toList());
        } catch (IOException e) {
            // Nothing to do and nobody to tell: the answer is returned either way.
        }
    }

    /** One cached answer: the key, when it was recorded, and what the bureau said. */
    private record Entry(String id, Instant recordedAt, BureauOutcome outcome) {

        private static final int COLUMNS = 4;

        static Optional<Entry> parse(String line) {
            var cells = List.of(line.split(",", -1));
            if (cells.size() != COLUMNS) {
                return Optional.empty();
            }
            return outcome(cells.get(2).trim(), cells.get(3).trim())
                    .flatMap(what -> recordedAt(cells.get(1).trim())
                            .map(when -> new Entry(cells.getFirst().trim(), when, what)));
        }

        String line() {
            return switch (outcome) {
                case BureauOutcome.Clear ignored -> id + "," + recordedAt + ",CLEAR,";
                case BureauOutcome.Sanctioned(var list) -> id + "," + recordedAt + ",SANCTIONED," + list;
                case BureauOutcome.Unavailable(var reason) -> throw new IllegalStateException(
                        "an unavailable bureau is never an entry: " + reason);
            };
        }

        private static Optional<BureauOutcome> outcome(String word, String list) {
            return switch (word) {
                case "CLEAR" -> Optional.of(new BureauOutcome.Clear());
                case "SANCTIONED" -> Optional.of(new BureauOutcome.Sanctioned(list));
                default -> Optional.empty();
            };
        }

        private static Optional<Instant> recordedAt(String value) {
            try {
                return Optional.of(Instant.parse(value));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }
}
