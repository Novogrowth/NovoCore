package gr.novotrade.novocore.core.ledger;

import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One balanced journal entry — brief §6's lower layer.
 *
 * <p><strong>The balance invariant is not here.</strong> {@code CLAUDE.md} rule 6 says "structurally",
 * and a Java check inside this class would hold only for callers who came through it. It is a deferred
 * constraint trigger in V15, checked at commit, which is what makes it hold for a hand-written
 * {@code psql} transaction too. The service checks it as well, purely so the failure names both totals.
 *
 * <p><strong>No totals columns.</strong> Storing {@code total_debits} and {@code total_credits} would
 * make the CHECK a single-row one and the invariant much easier to express — and they would be a second
 * copy of what the lines already say, free to disagree with them. Same argument that keeps
 * {@code normal_balance_side} off {@code Account} and a quantity off a serial-tracked lot.
 *
 * <p><strong>No {@code reversedBy}.</strong> One direction of the reversal link is stored; the other is
 * a query, for the same reason.
 */
@Entity
@Table(name = "journal_entry")
class JournalEntry extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The accounting date. Distinct from {@code createdAt}: backdating is ordinary. */
    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    /**
     * What produced this entry. Decides the Q13 correction policy, which is why it is stored rather
     * than inferred from the lines, and why the database refuses to let it change.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 40)
    private JournalSource source;

    /**
     * A plain id rather than a self-association. Nothing here needs to navigate to the reversed entry
     * — the service loads it by id when it validates the mirror — and a {@code @ManyToOne} to the same
     * entity invites a lazy proxy being walked in a projection.
     */
    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    /**
     * Cascaded and orphan-removing, which is exactly the pair the two write paths need: posting
     * creates an entry and its lines in one transaction, and an amendment (Q13) replaces the whole
     * list. Nothing deletes an <em>entry</em> — the database refuses that by trigger — so the REMOVE
     * half of the cascade is unreachable for the entry itself and present only so orphan removal
     * works.
     */
    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<JournalLine> lines = new ArrayList<>();

    /** For JPA only. */
    protected JournalEntry() {
    }

    JournalEntry(LocalDate entryDate, String description, JournalSource source, Long reversalOfId) {
        this.entryDate = entryDate;
        this.description = description;
        this.source = source;
        this.reversalOfId = reversalOfId;
    }

    Long getId() {
        return id;
    }

    LocalDate getEntryDate() {
        return entryDate;
    }

    String getDescription() {
        return description;
    }

    JournalSource getSource() {
        return source;
    }

    Long getReversalOfId() {
        return reversalOfId;
    }

    boolean isReversal() {
        return reversalOfId != null;
    }

    /**
     * The lines, by line number.
     *
     * <p>Sorted here rather than relying on {@code @OrderBy} alone, which orders the list Hibernate
     * <em>loads</em> and does nothing for an entry built in memory a moment ago. That is V12's lesson,
     * where one projection returned two different orders and a test written against the second case
     * never saw it.
     */
    List<JournalLine> getLines() {
        return lines.stream()
                .sorted(Comparator.comparingInt(JournalLine::getLineNumber))
                .toList();
    }

    /**
     * Replaces every line, renumbering from zero.
     *
     * <p>Never merges. A partial change would leave the remainder in a state nobody chose — the
     * argument that makes a chart-of-accounts reorder name every member and {@code BundleService.define}
     * replace the whole component list.
     */
    void replaceLines(List<JournalLine> replacements) {
        lines.clear();
        for (JournalLine line : replacements) {
            addLine(line);
        }
    }

    void addLine(JournalLine line) {
        line.attachTo(this, lines.size());
        lines.add(line);
    }

    /** Amended in place — permitted only for the sources Q13 names, which the service checks first. */
    void amendHeader(LocalDate newEntryDate, String newDescription) {
        this.entryDate = newEntryDate;
        this.description = newDescription;
    }
}
