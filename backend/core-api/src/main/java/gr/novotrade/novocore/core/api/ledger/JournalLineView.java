package gr.novotrade.novocore.core.api.ledger;

import gr.novotrade.novocore.core.api.account.AccountType;
import gr.novotrade.novocore.core.api.account.BalanceSide;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One posted journal line, as everything outside the core sees it.
 *
 * <p><strong>It carries its entry's date, source and description as well as its own.</strong> That is
 * not redundancy for its own sake: the same projection has to serve two readings — the lines
 * <em>within</em> an entry, and an account's ledger, which is a list of lines from many entries. A
 * separate type for the second reading would be the same fields in a different order, and the running
 * balance a ledger screen wants is presentation that belongs with the report (phase 8), not with the
 * line.
 *
 * <p>There is no {@code signedAmount()}. The side is the sign, and offering both invites code that adds
 * a signed amount to an unsigned one. {@link #amountOn(BalanceSide)} is what a two-column ledger needs.
 *
 * @param accountName included so a ledger listing does not need a second lookup per line. It is a
 *     snapshot for display only — the account may since have been renamed, which is allowed precisely
 *     because posting rules key off {@code AccountSystemKey} rather than the name.
 * @param subLedgerRef the sub-ledger entity this line concerns, or null. Required on Control-account
 *     lines; genuinely present on some others, notably {@code Cost of goods sold}.
 * @param vat the VAT class and taxable base, present only on a VAT-account line — see
 *     {@link VatDimension}.
 */
public record JournalLineView(
        long id,
        long entryId,
        LocalDate entryDate,
        JournalSource source,
        String entryDescription,
        int lineNumber,
        long accountId,
        String accountName,
        AccountType accountType,
        BalanceSide side,
        Money amount,
        String description,
        SubLedgerRef subLedgerRef,
        VatDimension vat) {

    public JournalLineView {
        Objects.requireNonNull(entryDate, "entryDate");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(entryDescription, "entryDescription");
        Objects.requireNonNull(accountName, "accountName");
        Objects.requireNonNull(accountType, "accountType");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(amount, "amount");
    }

    /** This line's amount if it is on the given side, otherwise zero. For a two-column ledger. */
    public Money amountOn(BalanceSide which) {
        Objects.requireNonNull(which, "which");
        return side == which ? amount : Money.zero(amount.currency());
    }

    /**
     * The effect of this line on a debit-positive running total: the amount for a debit, its negation
     * for a credit.
     *
     * <p>Debit-positive rather than normal-balance-positive, because a running total has to be
     * account-agnostic to be summable at all; presenting it on the account's own side is
     * {@link AccountBalance#onNormalSide()}'s job, once.
     */
    public Money debitPositiveEffect() {
        return side == BalanceSide.DEBIT ? amount : amount.negated();
    }

    public boolean isDebit() {
        return side == BalanceSide.DEBIT;
    }

    public Optional<String> descriptionIfAny() {
        return Optional.ofNullable(description);
    }

    public Optional<SubLedgerRef> subLedger() {
        return Optional.ofNullable(subLedgerRef);
    }

    public Optional<VatDimension> vatIfAny() {
        return Optional.ofNullable(vat);
    }
}
