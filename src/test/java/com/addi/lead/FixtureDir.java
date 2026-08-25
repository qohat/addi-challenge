package com.addi.lead;

import com.addi.lead.domain.Email;
import com.addi.lead.domain.Lead;
import com.addi.lead.domain.NationalId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

/**
 * The six CSVs written into a temporary directory, so a test states exactly the rows it depends on
 * instead of leaning on the fixtures the project ships. Unchecked on purpose: a test that cannot
 * write its own input has nothing to say about the code under test.
 */
public final class FixtureDir {

    public static final String ID = "1020304050";

    public static final Lead LEAD = new Lead(
            new NationalId(ID),
            "Ana",
            "Restrepo",
            LocalDate.of(1990, 3, 14),
            new Email("ana.restrepo@example.com"));

    private static final Map<String, String> HEADERS = Map.of(
            "leads.csv", "id,firstName,lastName,birthDate,email",
            "registry.csv", "id,firstName,lastName,birthDate,latencyMs,status",
            "judicial.csv", "id,recordCount,latencyMs,status",
            "bureau.csv", "id,sanctionsList,latencyMs,status",
            "fraud.csv", "id,fraudulent,latencyMs,status",
            "score.csv", "id,latencyMs,status");

    /** One file, its header and the rows given. A file never written is a missing file. */
    public static void write(Path dir, String file, String... rows) {
        try {
            Files.writeString(dir.resolve(file), HEADERS.get(file) + "\n" + String.join("\n", rows) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every system clean and instant for {@link #LEAD}. A test then rewrites the one file it is about. */
    public static void clean(Path dir) {
        write(dir, "leads.csv", ID + ",Ana,Restrepo,1990-03-14,ana.restrepo@example.com");
        write(dir, "registry.csv", ID + ",Ana,Restrepo,1990-03-14,0,UP");
        write(dir, "judicial.csv", ID + ",0,0,UP");
        write(dir, "bureau.csv", ID + ",,0,UP");
        write(dir, "fraud.csv", ID + ",false,0,UP");
        write(dir, "score.csv", ID + ",0,UP");
    }

    private FixtureDir() {}
}
