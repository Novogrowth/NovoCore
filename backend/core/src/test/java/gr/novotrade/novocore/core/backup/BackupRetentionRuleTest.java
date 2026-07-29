package gr.novotrade.novocore.core.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The rule that decides which backups are deleted.
 *
 * <p>Tested as pure logic against explicit dates, because this is the one component in the system
 * whose mistakes cannot be corrected afterwards. Everything else can be fixed and re-run; a
 * retention rule that deleted the wrong artefact has already deleted it.
 *
 * <p>The policy, as specified: <strong>the 7 most recent successful backups, plus the last
 * successful backup of each calendar month, forever.</strong>
 */
class BackupRetentionRuleTest {

    private static final ZoneId ATHENS = ZoneId.of("Europe/Athens");

    private final BackupRetentionRule rule =
            new BackupRetentionRule(7, Optional.empty(), ATHENS);

    @Nested
    @DisplayName("the rolling window")
    class RollingWindow {

        @Test
        @DisplayName("the seven most recent are kept whatever month they fall in")
        void sevenMostRecent() {
            // Fourteen consecutive days inside one month, so the monthly rule can only designate
            // one of them and the rolling window is what the rest depend on.
            List<BackupRetentionRule.Candidate> daily = dailyFrom(1, "2026-03-10T02:00", 14);

            List<BackupRetentionRule.Decision> decisions = rule.decide(daily);

            // Seven, not eight: the month's archive here is the newest backup, which the
            // rolling window already keeps. The two rules overlap rather than adding up.
            assertThat(retained(decisions)).hasSize(7);
            // The newest seven, by date.
            assertThat(retainedIds(decisions)).contains(14L, 13L, 12L, 11L, 10L, 9L, 8L);
            // The newest is also March's archive so far.
            assertThat(decisions.getFirst().monthlyArchive()).isTrue();
        }

        @Test
        @DisplayName("fewer backups than the window keeps all of them")
        void fewerThanTheWindow() {
            List<BackupRetentionRule.Decision> decisions = rule.decide(dailyFrom(1, "2026-03-10T02:00", 3));

            assertThat(decisions).hasSize(3).allSatisfy(decision ->
                    assertThat(decision.retained()).isTrue());
        }

        @Test
        @DisplayName("an empty history decides nothing rather than failing")
        void noBackupsAtAll() {
            assertThat(rule.decide(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("the monthly archive")
    class MonthlyArchive {

        @Test
        @DisplayName("the last backup of each month is kept forever, and the rest age out")
        void lastOfEachMonthSurvives() {
            // Three full months of daily backups. Only the rolling seven plus one archive per
            // month may survive; everything else is superseded.
            List<BackupRetentionRule.Candidate> candidates = new ArrayList<>();
            candidates.addAll(dailyFrom(100, "2026-01-01T02:00", 31));
            candidates.addAll(dailyFrom(200, "2026-02-01T02:00", 28));
            candidates.addAll(dailyFrom(300, "2026-03-01T02:00", 31));

            List<BackupRetentionRule.Decision> decisions = rule.decide(candidates);

            // 7 rolling + January's last + February's last. March's last is inside the rolling 7.
            assertThat(retained(decisions)).hasSize(9);
            assertThat(archived(decisions))
                    .as("one archive per calendar month, no more and no fewer")
                    .hasSize(3);
        }

        @Test
        @DisplayName("a month whose last day failed archives the most recent success instead")
        void monthEndingInFailureArchivesTheLastSuccess() {
            // The case the specification calls out. Only successful runs are passed in at all, so
            // a failed 30th and 31st simply are not here — and the rule must archive the 29th
            // rather than looking for a backup dated at month-end and finding none.
            List<BackupRetentionRule.Candidate> january = dailyFrom(100, "2026-01-01T02:00", 29);
            List<BackupRetentionRule.Candidate> march = dailyFrom(300, "2026-03-01T02:00", 10);
            List<BackupRetentionRule.Candidate> candidates = new ArrayList<>(january);
            candidates.addAll(march);

            List<BackupRetentionRule.Decision> decisions = rule.decide(candidates);

            BackupRetentionRule.Decision januaryArchive = decisions.stream()
                    .filter(BackupRetentionRule.Decision::monthlyArchive)
                    .filter(decision -> decision.backupRunId() < 300)
                    .findFirst()
                    .orElseThrow();
            assertThat(januaryArchive.backupRunId())
                    .as("the 29th, the last successful backup at or before month-end")
                    .isEqualTo(128L);
            assertThat(januaryArchive.retained()).isTrue();
        }

        @Test
        @DisplayName("a month with no backups designates nothing rather than reaching back")
        void monthWithNoBackupsArchivesNothing() {
            // February is entirely absent — the machine was off. The rule must not mark January's
            // archive as February's as well: it changes no outcome (it is kept either way) and
            // would make the history claim a backup exists for a month in which none was taken.
            List<BackupRetentionRule.Candidate> candidates = new ArrayList<>();
            candidates.addAll(dailyFrom(100, "2026-01-01T02:00", 31));
            candidates.addAll(dailyFrom(300, "2026-03-01T02:00", 5));

            List<BackupRetentionRule.Decision> decisions = rule.decide(candidates);

            assertThat(archived(decisions))
                    .as("January and March only")
                    .hasSize(2);
        }

        @Test
        @DisplayName("today's backup supersedes yesterday's as the month's archive")
        void theCurrentMonthsArchiveMoves() {
            List<BackupRetentionRule.Decision> firstDay =
                    rule.decide(dailyFrom(1, "2026-04-01T02:00", 1));
            assertThat(firstDay.getFirst().monthlyArchive()).isTrue();

            List<BackupRetentionRule.Decision> secondDay =
                    rule.decide(dailyFrom(1, "2026-04-01T02:00", 2));
            // The 2nd is now the month's archive and the 1st is not — but the 1st is still kept,
            // by the rolling window. Nothing is deleted by the handover, which is why the moving
            // archive needs no special handling.
            assertThat(secondDay.getFirst().monthlyArchive()).isTrue();
            assertThat(secondDay.getLast().monthlyArchive()).isFalse();
            assertThat(secondDay.getLast().retained()).isTrue();
        }
    }

    @Nested
    @DisplayName("the calendar zone")
    class CalendarZone {

        @Test
        @DisplayName("a backup just after midnight in Athens belongs to the month Athens is in")
        void zoneDecidesTheMonth() {
            // 01:30 on 1 March in Athens is 23:30 on 28 February in UTC. Getting this wrong would
            // archive the wrong artefact twelve times a year, with no symptom.
            List<BackupRetentionRule.Candidate> candidates = List.of(
                    new BackupRetentionRule.Candidate(1L, athens("2026-02-27T02:00")),
                    new BackupRetentionRule.Candidate(2L, athens("2026-03-01T01:30")));

            List<BackupRetentionRule.Decision> inAthens = rule.decide(candidates);
            assertThat(archived(inAthens))
                    .as("two months, so two archives")
                    .hasSize(2);

            List<BackupRetentionRule.Decision> inUtc =
                    new BackupRetentionRule(7, Optional.empty(), ZoneId.of("UTC"))
                            .decide(candidates);
            assertThat(archived(inUtc))
                    .as("in UTC both fall in February, so there is only one archive — which is "
                            + "the wrong answer for a business in Greece")
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("a monthly limit, for the setting that allows one")
    class MonthlyLimit {

        @Test
        @DisplayName("only the most recent months' archives survive a cap")
        void capDropsTheOldestMonths() {
            BackupRetentionRule capped = new BackupRetentionRule(7, Optional.of(2), ATHENS);

            List<BackupRetentionRule.Candidate> candidates = new ArrayList<>();
            candidates.addAll(dailyFrom(100, "2026-01-01T02:00", 5));
            candidates.addAll(dailyFrom(200, "2026-02-01T02:00", 5));
            candidates.addAll(dailyFrom(300, "2026-03-01T02:00", 5));
            candidates.addAll(dailyFrom(400, "2026-04-01T02:00", 5));

            List<BackupRetentionRule.Decision> decisions = capped.decide(candidates);

            // January's archive is outside the cap and outside the rolling seven, so it goes.
            BackupRetentionRule.Decision january = decisions.stream()
                    .filter(decision -> decision.backupRunId() == 104L)
                    .findFirst().orElseThrow();
            assertThat(january.monthlyArchive())
                    .as("still a fact about the data")
                    .isTrue();
            assertThat(january.retained())
                    .as("but outside the configured limit")
                    .isFalse();
            assertThat(january.reason()).contains("outside the monthly limit");
        }
    }

    @Test
    @DisplayName("every decision explains itself")
    void decisionsAreLegible() {
        // The reason is written into the log line when an artefact is deleted. A deletion nobody
        // can account for afterwards is the thing that makes people distrust automation.
        List<BackupRetentionRule.Decision> decisions = rule.decide(dailyFrom(1, "2026-05-01T02:00", 20));

        assertThat(decisions).allSatisfy(decision ->
                assertThat(decision.reason()).isNotBlank());
        assertThat(decisions.getLast().reason()).contains("superseded");
    }

    // -------------------------------------------------------------------------------------

    /**
     * {@code count} consecutive daily backups from {@code startLocal}, ids {@code baseId}..
     *
     * <p>The base id is a parameter rather than always 1, and that is not cosmetic: the first
     * version of this helper restarted at 1 on every call, so a test composing three months handed
     * the rule three backups all called id 1. Four tests failed and every one of them looked like
     * a bug in the retention rule. Ids are the rule's identity for a backup, so a fixture that
     * reuses them is testing something that cannot happen.
     */
    private static List<BackupRetentionRule.Candidate> dailyFrom(long baseId, String startLocal,
            int count) {
        List<BackupRetentionRule.Candidate> candidates = new ArrayList<>();
        Instant start = athens(startLocal);
        for (int day = 0; day < count; day++) {
            candidates.add(new BackupRetentionRule.Candidate(
                    baseId + day, start.plus(java.time.Duration.ofDays(day))));
        }
        return candidates;
    }

    private static Instant athens(String local) {
        return LocalDateTime.parse(local).atZone(ATHENS).toInstant();
    }

    private static List<BackupRetentionRule.Decision> retained(
            List<BackupRetentionRule.Decision> decisions) {
        return decisions.stream().filter(BackupRetentionRule.Decision::retained).toList();
    }

    private static List<Long> retainedIds(List<BackupRetentionRule.Decision> decisions) {
        return retained(decisions).stream()
                .map(BackupRetentionRule.Decision::backupRunId)
                .toList();
    }

    private static List<BackupRetentionRule.Decision> archived(
            List<BackupRetentionRule.Decision> decisions) {
        return decisions.stream().filter(BackupRetentionRule.Decision::monthlyArchive).toList();
    }
}
