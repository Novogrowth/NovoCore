package gr.novotrade.novocore.core.api.ledger;

import gr.novotrade.novocore.core.api.account.BalanceSide;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import java.util.Objects;
import java.util.Optional;

/**
 * One side of one line of a journal entry: an account, a side, and a positive amount.
 *
 * <p><strong>A named side and a positive amount, never a signed one.</strong> Representing a credit as
 * a negative debit makes {@code CLAUDE.md} rule 6 a question of whether every producer of a line
 * remembered to negate; naming the side makes the balance check a sum per side, which no accidental sign
 * can satisfy. The database says the same thing — {@code amount > 0} is a CHECK. See {@link BalanceSide}.
 *
 * <p>Zero is refused as well as negative. A zero line states nothing, and an entry padded with them
 * balances trivially.
 *
 * @param accountId the account posted to. Must be active, unless this line belongs to a reversal — an
 *     entry that has already been posted must stay correctable after the account it used is retired.
 * @param description optional per-line narrative. The entry's own description carries the general
 *     explanation; this is for the line that needs its own.
 * @param subLedgerRef required when the account's kind is {@code CONTROL}, and its type must match that
 *     account's declared sub-ledger (brief §6). Permitted on other accounts and genuinely used there:
 *     {@code Cost of goods sold} is a Standard account whose lines carry lot references, because brief
 *     §6 requires one line per lot consumed.
 * @param vat present only on a line posting to a VAT account — see {@link VatDimension}.
 */
public record NewJournalLine(
        long accountId,
        @Mandatory BalanceSide side,
        @Mandatory Money amount,
        String description,
        SubLedgerRef subLedgerRef,
        VatDimension vat) {

    public NewJournalLine {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(amount, "amount");
        if (accountId <= 0) {
            throw new IllegalArgumentException(
                    "accountId must be a positive NovoCore id, got " + accountId);
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException(
                    "Journal line amount " + amount + " is not positive. Which way a line moves is "
                            + "said by its side, not by its sign, so a negative amount is a credit "
                            + "written as a debit — and a zero one states nothing while still "
                            + "balancing. Post the opposite side instead.");
        }
        description = (description == null || description.isBlank()) ? null : description.trim();
    }

    public static NewJournalLine debit(long accountId, Money amount) {
        return new NewJournalLine(accountId, BalanceSide.DEBIT, amount, null, null, null);
    }

    public static NewJournalLine credit(long accountId, Money amount) {
        return new NewJournalLine(accountId, BalanceSide.CREDIT, amount, null, null, null);
    }

    public static NewJournalLine of(long accountId, BalanceSide side, Money amount) {
        return new NewJournalLine(accountId, side, amount, null, null, null);
    }

    /** The same line with a narrative of its own. */
    public NewJournalLine describedAs(String lineDescription) {
        return new NewJournalLine(accountId, side, amount, lineDescription, subLedgerRef, vat);
    }

    /** The same line pointing at the sub-ledger entity it concerns. */
    public NewJournalLine forSubLedger(SubLedgerRef ref) {
        Objects.requireNonNull(ref, "ref");
        return new NewJournalLine(accountId, side, amount, description, ref, vat);
    }

    /** The same line carrying the VAT class and taxable base it was computed from. */
    public NewJournalLine withVat(VatDimension vatDimension) {
        Objects.requireNonNull(vatDimension, "vatDimension");
        return new NewJournalLine(accountId, side, amount, description, subLedgerRef, vatDimension);
    }

    /** The mirror of this line: same account, same amount, same references, opposite side. */
    public NewJournalLine mirrored() {
        return new NewJournalLine(accountId, side.opposite(), amount, description, subLedgerRef, vat);
    }

    public Optional<SubLedgerRef> subLedger() {
        return Optional.ofNullable(subLedgerRef);
    }

    public Optional<VatDimension> vatIfAny() {
        return Optional.ofNullable(vat);
    }

    public boolean isDebit() {
        return side == BalanceSide.DEBIT;
    }
}
