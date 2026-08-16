package com.addi.lead.adapter;

import static com.addi.lead.FixtureDir.ID;
import static com.addi.lead.FixtureDir.LEAD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.addi.lead.FixtureDir;
import com.addi.lead.domain.BureauOutcome;
import com.addi.lead.domain.JudicialOutcome;
import com.addi.lead.domain.RegistryOutcome;
import com.addi.lead.domain.ScoreOutcome;
import com.addi.lead.port.Latency;
import com.addi.lead.port.Randomness;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Every adapter against fixtures the test wrote, with both effects faked. No sleeping, no dice. */
class FixtureAdaptersTest {

    @TempDir
    Path dir;

    private final List<Duration> pauses = new ArrayList<>();
    private final List<Integer> bounds = new ArrayList<>();

    private record RecordingLatency(List<Duration> pauses) implements Latency {
        @Override
        public void pause(Duration duration) {
            pauses.add(duration);
        }
    }

    private record RecordingRandomness(List<Integer> bounds, int value) implements Randomness {
        @Override
        public int nextInt(int bound) {
            bounds.add(bound);
            return value;
        }
    }

    private FixtureNationalRegistry registry() {
        return new FixtureNationalRegistry(dir, new RecordingLatency(pauses));
    }

    private FixtureJudicialRecords judicial() {
        return new FixtureJudicialRecords(dir, new RecordingLatency(pauses));
    }

    private FixtureComplianceBureau bureau() {
        return new FixtureComplianceBureau(dir, new RecordingLatency(pauses));
    }

    private FixtureQualificationScore score(Randomness randomness) {
        return new FixtureQualificationScore(dir, new RecordingLatency(pauses), randomness);
    }

    private static Duration millis(long value) {
        return Duration.ofMillis(value);
    }

    @Test
    void aMatchingRegistryRowMatches() {
        FixtureDir.clean(dir);

        assertEquals(new RegistryOutcome.Matched(), registry().check(LEAD));
    }

    @Test
    void aDifferingRegistryRowReportsTheFields() {
        FixtureDir.clean(dir);
        FixtureDir.write(dir, "registry.csv", ID + ",Ana,Restrepo,1991-03-14,0,UP");

        assertEquals(new RegistryOutcome.Mismatch(List.of("birthDate")), registry().check(LEAD));
    }

    @Test
    void judicialRejectsOnOneRecordAndPassesOnZero() {
        FixtureDir.clean(dir);
        FixtureDir.write(dir, "judicial.csv", ID + ",1,0,UP");
        assertEquals(new JudicialOutcome.RecordsFound(1), judicial().check(LEAD));

        FixtureDir.write(dir, "judicial.csv", ID + ",0,0,UP");
        assertEquals(new JudicialOutcome.Clear(), judicial().check(LEAD));
    }

    @Test
    void aNonEmptySanctionsListIsSanctionedAndAnEmptyOneIsClear() {
        FixtureDir.clean(dir);
        FixtureDir.write(dir, "bureau.csv", ID + ",OFAC,0,UP");
        assertEquals(new BureauOutcome.Sanctioned("OFAC"), bureau().screen(LEAD));

        FixtureDir.write(dir, "bureau.csv", ID + ",,0,UP");
        assertEquals(new BureauOutcome.Clear(), bureau().screen(LEAD));
    }

    @Test
    void aDownSystemIsUnavailableAtEveryStep() {
        FixtureDir.write(dir, "registry.csv", ID + ",Ana,Restrepo,1990-03-14,0,DOWN");
        FixtureDir.write(dir, "judicial.csv", ID + ",0,0,DOWN");
        FixtureDir.write(dir, "bureau.csv", ID + ",,0,DOWN");
        FixtureDir.write(dir, "score.csv", ID + ",0,DOWN");

        assertInstanceOf(RegistryOutcome.Unavailable.class, registry().check(LEAD));
        assertInstanceOf(JudicialOutcome.Unavailable.class, judicial().check(LEAD));
        assertInstanceOf(BureauOutcome.Unavailable.class, bureau().screen(LEAD));
        assertInstanceOf(ScoreOutcome.Unavailable.class, score(new RecordingRandomness(bounds, 75)).score(LEAD));
    }

    @Test
    void aMissingRowMeansTheSystemHasNothingOnThisPerson() {
        FixtureDir.clean(dir);
        FixtureDir.write(dir, "registry.csv", "9999999999,Bob,Other,1980-01-01,0,UP");
        FixtureDir.write(dir, "judicial.csv", "9999999999,3,0,UP");
        FixtureDir.write(dir, "bureau.csv", "9999999999,OFAC,0,UP");
        FixtureDir.write(dir, "score.csv", "9999999999,0,DOWN");

        assertEquals(new RegistryOutcome.NotFound(), registry().check(LEAD));
        assertEquals(new JudicialOutcome.Clear(), judicial().check(LEAD));
        assertEquals(new BureauOutcome.Clear(), bureau().screen(LEAD));
        assertEquals(new ScoreOutcome.Scored(75), score(new RecordingRandomness(bounds, 75)).score(LEAD));
        assertEquals(List.of(), pauses, "a row that does not exist states no latency");
    }

    @Test
    void eachAdapterRequestsExactlyTheLatencyItsRowStates() {
        FixtureDir.clean(dir);
        FixtureDir.write(dir, "registry.csv", ID + ",Ana,Restrepo,1990-03-14,120,UP");
        FixtureDir.write(dir, "judicial.csv", ID + ",0,80,UP");
        FixtureDir.write(dir, "bureau.csv", ID + ",,200,UP");
        FixtureDir.write(dir, "score.csv", ID + ",60,UP");

        registry().check(LEAD);
        judicial().check(LEAD);
        bureau().screen(LEAD);
        score(new RecordingRandomness(bounds, 75)).score(LEAD);

        assertEquals(List.of(millis(120), millis(80), millis(200), millis(60)), pauses);
    }

    @Test
    void aDownSystemStillCostsTheLatencyItStates() {
        FixtureDir.write(dir, "bureau.csv", ID + ",,340,DOWN");

        assertInstanceOf(BureauOutcome.Unavailable.class, bureau().screen(LEAD));
        assertEquals(List.of(millis(340)), pauses);
    }

    @Test
    void theScoreAsksForAnInclusiveZeroToHundred() {
        FixtureDir.clean(dir);

        score(new RecordingRandomness(bounds, 0)).score(LEAD);

        assertEquals(List.of(101), bounds);
    }

    @Test
    void aSeededRandomnessPortGivesTheSameScoreTwice() {
        FixtureDir.clean(dir);

        var first = score(RandomNumbers.from(OptionalLong.of(7))).score(LEAD);
        var second = score(RandomNumbers.from(OptionalLong.of(7))).score(LEAD);

        assertEquals(first, second);
    }

    @Test
    void anUnseededScoreStaysInsideTheInclusiveRange() {
        FixtureDir.clean(dir);
        var port = score(RandomNumbers.from(OptionalLong.empty()));

        for (var run = 0; run < 500; run++) {
            var value = assertInstanceOf(ScoreOutcome.Scored.class, port.score(LEAD)).value();
            assertTrue(value >= 0 && value <= 100, "score out of range: " + value);
        }
    }

    @Test
    void aMalformedRowIsUnavailableAtEveryStep() {
        FixtureDir.write(dir, "registry.csv", ID + ",Ana,Restrepo,1990-03-14,not-a-number,UP");
        FixtureDir.write(dir, "judicial.csv", ID + ",0,also-not,UP");
        FixtureDir.write(dir, "bureau.csv", ID + ",,0,SIDEWAYS");
        FixtureDir.write(dir, "score.csv", ID);

        assertInstanceOf(RegistryOutcome.Unavailable.class, registry().check(LEAD));
        assertInstanceOf(JudicialOutcome.Unavailable.class, judicial().check(LEAD));
        assertInstanceOf(BureauOutcome.Unavailable.class, bureau().screen(LEAD));
        assertInstanceOf(ScoreOutcome.Unavailable.class, score(new RecordingRandomness(bounds, 75)).score(LEAD));
    }

    @Test
    void aMissingFixtureFileIsUnavailableAtEveryStep() {
        assertInstanceOf(RegistryOutcome.Unavailable.class, registry().check(LEAD));
        assertInstanceOf(JudicialOutcome.Unavailable.class, judicial().check(LEAD));
        assertInstanceOf(BureauOutcome.Unavailable.class, bureau().screen(LEAD));
        assertInstanceOf(ScoreOutcome.Unavailable.class, score(new RecordingRandomness(bounds, 75)).score(LEAD));
    }

    @Test
    void theLocalDatabaseIsSeededFromLeadsCsv() {
        FixtureDir.clean(dir);

        assertEquals(Optional.of(LEAD), InMemoryLeadRepository.fromFixtures(dir).findById(LEAD.id()));
    }

    @Test
    void anUnreadableSeedLeavesTheLocalDatabaseEmpty() {
        assertEquals(Optional.empty(), InMemoryLeadRepository.fromFixtures(dir).findById(LEAD.id()));
    }
}
