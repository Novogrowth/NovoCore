package gr.novotrade.novocore.core.api.account;

/**
 * A side of a journal entry.
 *
 * <p>An enum rather than a signed amount, because {@code CLAUDE.md} rule 6 requires that debits
 * equal credits structurally. Representing a credit as a negative debit makes that invariant a
 * question of whether every producer of a line remembered to negate; naming the side makes the
 * balance check a sum per side, which cannot be satisfied by an accidental sign.
 */
public enum BalanceSide {
    DEBIT,
    CREDIT;

    public BalanceSide opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
