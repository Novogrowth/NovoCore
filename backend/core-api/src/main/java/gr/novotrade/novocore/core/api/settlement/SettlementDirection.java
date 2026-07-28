package gr.novotrade.novocore.core.api.settlement;

import gr.novotrade.novocore.core.api.ledger.JournalSource;

/**
 * Which way the money went.
 *
 * <p>Together with {@link PartyType} this is what lets Receipt and Payment share one table without
 * the ledger being able to tell: the direction decides the side of the entry and which
 * {@link JournalSource} it carries, so Q13's per-source correction policy is unchanged.
 */
public enum SettlementDirection {

    /** Money in. Debit our account, credit the party's control account. Posts as a Receipt. */
    INCOMING(JournalSource.RECEIPT),

    /** Money out. Debit the party's control account, credit our account. Posts as a Payment. */
    OUTGOING(JournalSource.PAYMENT);

    private final JournalSource source;

    SettlementDirection(JournalSource source) {
        this.source = source;
    }

    /** The journal source an entry from this direction carries, and therefore its Q13 policy. */
    public JournalSource journalSource() {
        return source;
    }

    public boolean isIncoming() {
        return this == INCOMING;
    }
}
