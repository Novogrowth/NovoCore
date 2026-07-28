package gr.novotrade.novocore.core.api.ledger;

import gr.novotrade.novocore.core.api.shared.Money;

/**
 * Debits do not equal credits. {@code CLAUDE.md} rule 6, refused.
 *
 * <p>The service throws this before the database is touched, purely so the failure names both totals and
 * the difference — which is what somebody needs to find the missing or mistyped line. <strong>The
 * guarantee is not this exception</strong>: it is the deferred constraint trigger
 * {@code journal_entry_must_balance}, which holds against a manual {@code psql} session and any future
 * adapter, neither of which comes through here.
 */
public class UnbalancedJournalEntryException extends RuntimeException {

    public UnbalancedJournalEntryException(Money debits, Money credits) {
        super("Journal entry does not balance: debits " + debits + " against credits " + credits
                + ", a difference of " + debits.minus(credits) + ". Debits must equal credits "
                + "(CLAUDE.md rule 6).");
    }
}
