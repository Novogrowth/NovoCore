package gr.novotrade.novocore.core.backup;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which backups are kept, as specified: <strong>the 7 most recent successful backups, rolling,
 * plus the last successful backup of each calendar month, forever.</strong>
 *
 * <p>Pure logic with no database and no filesystem, so the rule that decides what gets deleted is
 * testable directly against a list of dates. That separation is worth more here than anywhere else
 * in this codebase: every other component's bugs can be corrected afterwards, and this one's
 * cannot.
 *
 * <h2>"The last backup of the month" needs no month-end special case</h2>
 *
 * <p>The specification says the monthly archive is the most recent successful backup at or before
 * month-end — so a month whose run failed on the 31st archives the 30th's instead. Stated
 * positively, that is simply: <em>a backup is its month's archive if and only if no later
 * successful backup exists in the same calendar month.</em> No calendar arithmetic, no "is this
 * the last day", and it is automatically right when a month has runs on some days and not others.
 *
 * <p>A month with <strong>no</strong> successful backup designates nothing, deliberately. Reaching
 * back to re-designate an earlier month's artefact would mark one artefact as two months'
 * archives, which changes no retention outcome — it is already kept — while making the history
 * claim a backup exists for a month in which none was taken.
 *
 * <p>During the current month the archive moves: today's backup is the latest in its month, and
 * tomorrow's takes over. That is correct and needs no handling, because the superseded one is
 * still inside the rolling 7 and only stops being retained once it has fallen out of both rules.
 *
 * <h2>The zone is load-bearing</h2>
 *
 * <p>A backup taken at 01:30 on the 1st of a month in Athens is still the previous month in UTC.
 * Deciding "which month is this in" without a zone would archive the wrong artefact twelve times a
 * year, silently.
 */
final class BackupRetentionRule {

    private final int dailyCount;
    private final Optional<Integer> monthlyCount;
    private final ZoneId zone;

    /**
     * @param dailyCount how many of the most recent successful backups are kept regardless of date
     * @param monthlyCount how many calendar-month archives to keep, empty for forever
     */
    BackupRetentionRule(int dailyCount, Optional<Integer> monthlyCount, ZoneId zone) {
        this.dailyCount = dailyCount;
        this.monthlyCount = monthlyCount;
        this.zone = zone;
    }

    /**
     * Decides each backup's fate.
     *
     * @param candidates every <em>successful</em> backup, in any order. Failed runs are not passed
     *     in: they have no artefact to keep or delete, and counting them towards the rolling 7
     *     would let a run of failures evict good backups.
     * @return one decision per candidate, newest first
     */
    List<Decision> decide(List<Candidate> candidates) {
        List<Candidate> newestFirst = candidates.stream()
                .sorted(Comparator.comparing(Candidate::takenAt).reversed()
                        .thenComparing(Comparator.comparingLong(Candidate::id).reversed()))
                .toList();

        Set<Long> withinDailyWindow = new LinkedHashSet<>();
        newestFirst.stream().limit(dailyCount).forEach(candidate ->
                withinDailyWindow.add(candidate.id()));

        // The first candidate seen for a month is, by the sort above, the latest in that month.
        Map<YearMonth, Long> archiveByMonth = new HashMap<>();
        for (Candidate candidate : newestFirst) {
            archiveByMonth.putIfAbsent(monthOf(candidate), candidate.id());
        }

        // Months newest first, so a monthly cap drops the oldest months rather than an arbitrary
        // selection of them.
        List<YearMonth> monthsNewestFirst = archiveByMonth.keySet().stream()
                .sorted(Comparator.reverseOrder())
                .toList();
        Set<Long> keptArchives = new LinkedHashSet<>();
        monthlyCount
                .map(count -> monthsNewestFirst.stream().limit(count).toList())
                .orElse(monthsNewestFirst)
                .forEach(month -> keptArchives.add(archiveByMonth.get(month)));

        return newestFirst.stream().map(candidate -> {
            // Objects.equals, not ==, so a future change that lets the lookup miss produces false
            // rather than a NullPointerException from unboxing.
            boolean isMonthArchive =
                    java.util.Objects.equals(archiveByMonth.get(monthOf(candidate)), candidate.id());
            boolean daily = withinDailyWindow.contains(candidate.id());
            boolean archived = keptArchives.contains(candidate.id());
            return new Decision(candidate.id(), daily || archived, isMonthArchive,
                    reason(daily, archived, isMonthArchive, monthOf(candidate)));
        }).toList();
    }

    private YearMonth monthOf(Candidate candidate) {
        return YearMonth.from(candidate.takenAt().atZone(zone));
    }

    private String reason(boolean daily, boolean archived, boolean isMonthArchive,
            YearMonth month) {
        if (daily && archived) {
            return "within the most recent %d, and the archive for %s".formatted(dailyCount, month);
        }
        if (daily) {
            return "within the most recent %d".formatted(dailyCount);
        }
        if (archived) {
            return "the last successful backup of %s".formatted(month);
        }
        if (isMonthArchive) {
            return "the archive for %s, but outside the monthly limit".formatted(month);
        }
        return "superseded: outside the most recent %d, and a later backup exists in %s"
                .formatted(dailyCount, month);
    }

    /** @param takenAt the run's start, which is what dates the artefact */
    record Candidate(long id, Instant takenAt) {
    }

    /**
     * @param retained false means the artefact is deleted — locally and at every destination. The
     *     {@code backup_run} row itself is never deleted; it becomes the record that a backup was
     *     taken and has since aged out, which is what makes the history continuous.
     * @param monthlyArchive whether this is its calendar month's archive, as a fact about the data
     *     rather than about the current limits
     */
    record Decision(long backupRunId, boolean retained, boolean monthlyArchive, String reason) {
    }
}
