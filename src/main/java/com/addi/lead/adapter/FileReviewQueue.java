package com.addi.lead.adapter;

import com.addi.lead.domain.CaseId;
import com.addi.lead.domain.CaseStatus;
import com.addi.lead.domain.Checkpoint;
import com.addi.lead.domain.Email;
import com.addi.lead.domain.Lead;
import com.addi.lead.domain.NationalId;
import com.addi.lead.domain.ReviewCase;
import com.addi.lead.domain.Step;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The review queue as one file per case, named by the case ID (ADR 0003). A directory listing is
 * then the queue and {@code cat} is the case, which is why neither has a command.
 *
 * <p>Unlike the cache this propagates a failed write: losing a cache entry costs a screening, and
 * losing a case loses the analyst's only record that anything is waiting.
 */
public record FileReviewQueue(Path directory) {

    public CaseId open(Checkpoint checkpoint, Instant now) throws IOException {
        var id = new CaseId(checkpoint.lead().id().value() + "-" + now.toEpochMilli());
        write(new ReviewCase(id, now, checkpoint, CaseStatus.OPEN));
        return id;
    }

    /** A file that cannot be read or parsed is no case, exactly as the cache treats an unreadable row. */
    public Optional<ReviewCase> find(CaseId id) {
        try {
            return Optional.of(parse(fields(Files.readAllLines(directory.resolve(id.value())))));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void resolve(ReviewCase reviewCase, CaseStatus status) throws IOException {
        write(new ReviewCase(reviewCase.id(), reviewCase.openedAt(), reviewCase.checkpoint(), status));
    }

    private void write(ReviewCase reviewCase) throws IOException {
        Atomic.write(directory.resolve(reviewCase.id().value()), lines(reviewCase));
    }

    /**
     * {@code key=value} rather than the fixtures' CSV: a checkpoint reason is adapter prose and will
     * contain commas. The lead is stored in full because the CRM record may since have changed and the
     * case is what the analyst reviewed (ADR 0004).
     */
    private static List<String> lines(ReviewCase reviewCase) {
        var checkpoint = reviewCase.checkpoint();
        var lead = checkpoint.lead();
        return List.of(
                "id=" + reviewCase.id().value(),
                "openedAt=" + reviewCase.openedAt(),
                "status=" + reviewCase.status(),
                "pending=" + checkpoint.pending(),
                "reason=" + checkpoint.reason(),
                "leadId=" + lead.id().value(),
                "firstName=" + lead.firstName(),
                "lastName=" + lead.lastName(),
                "birthDate=" + lead.birthDate(),
                "email=" + lead.email().value());
    }

    /** Split on the first {@code =} only, so the value keeps every one after it. */
    private static Map<String, String> fields(List<String> lines) {
        return lines.stream()
                .map(line -> line.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (first, second) -> first));
    }

    /** Every key is required, and every unparseable value throws its way out to {@link #find}. */
    private static ReviewCase parse(Map<String, String> fields) {
        var lead = new Lead(
                new NationalId(required(fields, "leadId")),
                required(fields, "firstName"),
                required(fields, "lastName"),
                LocalDate.parse(required(fields, "birthDate")),
                new Email(required(fields, "email")));
        return new ReviewCase(
                new CaseId(required(fields, "id")),
                Instant.parse(required(fields, "openedAt")),
                new Checkpoint(lead, Step.valueOf(required(fields, "pending")), required(fields, "reason")),
                CaseStatus.valueOf(required(fields, "status")));
    }

    private static String required(Map<String, String> fields, String key) {
        var value = fields.get(key);
        if (value == null) {
            throw new IllegalArgumentException("no " + key + " in the case file");
        }
        return value;
    }
}
