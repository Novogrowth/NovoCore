package gr.novotrade.novocore.core.api.ledger;

import gr.novotrade.novocore.core.api.shared.Money;
import java.util.Objects;

/**
 * The VAT class a VAT journal line was computed at, and the net amount it was computed on.
 *
 * <p><strong>Why a journal line carries this at all — Q14.</strong> The answer to Q14 is that VAT is
 * computed <em>per line</em> and <em>summed by rate</em> for posting. That second half is what makes
 * this record necessary: an invoice with 13% lines and 24% lines posts two Output VAT lines, and
 * without the rate on the line they are two indistinguishable amounts against the same account. Summing
 * by rate would then be pointless — one could post a single total instead and lose nothing. So the rate
 * is a dimension of the posted line, not merely of the document behind it.
 *
 * <p>It also has to be here rather than only on the invoice, because a Manual Journal Entry can post to
 * a VAT account directly (an accountant's period adjustment is exactly that), and a VAT figure assembled
 * only from documents would silently omit it.
 *
 * <p><strong>The class, not the rate.</strong> Two seeded classes both charge 4% — {@code 1040} in its
 * own right and {@code 1041} as the island-reduced counterpart of 6% — so the rate is ambiguous as an
 * identity and the class id is what is stored. See {@code VatClassView}.
 *
 * <p><strong>Permitted only on a line posting to a VAT account</strong> — the accounts carrying
 * {@code AccountSystemKey.OUTPUT_VAT} or {@code INPUT_VAT}. Enforced by the service and by a trigger, so
 * a rate cannot be attached to a revenue line where nothing would ever read it. It is permitted rather
 * than required on those accounts, because the periodic settlement that clears both VAT accounts against
 * the bank is a movement of money at no rate at all.
 *
 * @param vatClassId the {@code VatClass} this line's VAT was computed at
 * @param taxableBase the net amount the rate was applied to. Strictly positive: a zero base produces no
 *     VAT and therefore no line, and a reversal mirrors the base unchanged while flipping the side.
 */
public record VatDimension(long vatClassId, Money taxableBase) {

    public VatDimension {
        Objects.requireNonNull(taxableBase, "taxableBase");
        if (vatClassId <= 0) {
            throw new IllegalArgumentException(
                    "vatClassId must be a positive NovoCore id, got " + vatClassId);
        }
        if (!taxableBase.isPositive()) {
            throw new IllegalArgumentException(
                    "Taxable base " + taxableBase + " is not positive. A base of zero yields no VAT "
                            + "and therefore no VAT line at all; a negative base is the other side of "
                            + "the entry, which is said by the line's side and not by its sign.");
        }
    }

    public static VatDimension of(long vatClassId, Money taxableBase) {
        return new VatDimension(vatClassId, taxableBase);
    }

    @Override
    public String toString() {
        return "VatClass#" + vatClassId + " on " + taxableBase;
    }
}
