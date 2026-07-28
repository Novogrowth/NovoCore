package gr.novotrade.novocore.core.api.purchasing;

import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One line of a supplier's invoice.
 *
 * <p>Two shapes, built by two factories rather than one call with optional arguments, because the
 * choice decides where the money is debited and is not a detail a caller should be able to leave
 * unstated. An {@link PurchaseLineType#INVENTORY} line is a quantity at a unit price and debits GR/IR
 * clearing; an {@link PurchaseLineType#EXPENSE} line is an amount against a named account.
 *
 * <p><strong>Every line states its VAT treatment, and exactly one of the two ways.</strong> Either a
 * {@code VatClass} — the rate the supplier charged, or the rate we self-assess under reverse charge —
 * or a {@code VatExemptionReason}, the named article that says no VAT applies. Neither is optional and
 * both together is refused: a line with no treatment at all is a number nobody can file a return from,
 * and a line with both states two different legal positions about the same money. A purchase from
 * outside the EU, where import VAT is settled at customs rather than by the supplier, is an exempt
 * line carrying the reason that actually applies — not a line with the question left blank.
 *
 * <p><strong>Reverse charge is a flag on the line, not an inference from the supplier.</strong> It
 * still has to agree with the supplier's {@code VatStatus}, and the service refuses a disagreement in
 * both directions rather than resolving it: an intra-EU B2B supplier's VAT-classed lines must be
 * reverse-charged, and no other supplier's may be. Deriving it silently would mean a change to a
 * supplier record quietly re-stating what past invoices claimed about VAT.
 *
 * @param matches the goods receipt lines this line is paying for, when the goods arrived first. Empty
 *     when the invoice arrives first — the Goods Receipt then names <em>this</em> line instead, and
 *     takes its unit cost from it, which is why that direction can never produce a variance.
 */
public record NewPurchaseInvoiceLine(
        PurchaseLineType type,
        Long productId,
        Quantity quantity,
        UnitCost unitPrice,
        Long expenseAccountId,
        Money amount,
        String description,
        Long vatClassId,
        Long vatExemptionReasonId,
        boolean reverseCharge,
        List<GoodsReceiptMatch> matches) {

    public NewPurchaseInvoiceLine {
        Objects.requireNonNull(type, "type");
        matches = matches == null ? List.of() : List.copyOf(matches);
        description = (description == null || description.isBlank()) ? null : description.trim();

        switch (type) {
            case INVENTORY -> {
                Objects.requireNonNull(productId, "productId is required on an inventory line");
                Objects.requireNonNull(quantity, "quantity is required on an inventory line");
                Objects.requireNonNull(unitPrice, "unitPrice is required on an inventory line");
                if (expenseAccountId != null || amount != null) {
                    throw new IllegalArgumentException(
                            "An inventory line is a quantity at a unit price, so it states neither an "
                                    + "account nor a total: both would be a second copy of a number "
                                    + "the first two already give, free to disagree with them.");
                }
                if (!quantity.isPositive()) {
                    throw new IllegalArgumentException(
                            "Invoice line quantity " + quantity + " is not positive. A returned "
                                    + "quantity is a credit note (Q26), not a negative invoice line.");
                }
            }
            case EXPENSE -> {
                Objects.requireNonNull(
                        expenseAccountId, "expenseAccountId is required on an expense line");
                Objects.requireNonNull(amount, "amount is required on an expense line");
                if (productId != null || quantity != null || unitPrice != null) {
                    throw new IllegalArgumentException(
                            "An expense line buys no stock, so it names no product, quantity or unit "
                                    + "price. If this line is stock, it is an inventory line and has "
                                    + "to reach the ledger through GR/IR clearing (ADR 0004).");
                }
                if (!amount.isPositive()) {
                    throw new IllegalArgumentException(
                            "Invoice line amount " + amount + " is not positive. A supplier credit is "
                                    + "its own document, not a negative line on this one.");
                }
                if (!matches.isEmpty()) {
                    throw new IllegalArgumentException(
                            "An expense line has nothing to receive, so it cannot be matched to a "
                                    + "goods receipt line. Only stock passes through GR/IR clearing.");
                }
            }
        }

        if ((vatClassId == null) == (vatExemptionReasonId == null)) {
            throw new IllegalArgumentException(
                    "A purchase invoice line states either a VAT class or a VAT exemption reason, and "
                            + "exactly one of them. With neither there is no answer to what VAT this "
                            + "line carried, and a VAT return cannot be assembled from a blank; with "
                            + "both there are two different legal positions about the same money.");
        }
        if (vatClassId != null && vatClassId <= 0) {
            throw new IllegalArgumentException(
                    "vatClassId must be a positive NovoCore id, got " + vatClassId);
        }
        if (vatExemptionReasonId != null && vatExemptionReasonId <= 0) {
            throw new IllegalArgumentException(
                    "vatExemptionReasonId must be a positive NovoCore id, got "
                            + vatExemptionReasonId);
        }
        if (reverseCharge && vatClassId == null) {
            throw new IllegalArgumentException(
                    "A reverse-charged line self-assesses VAT at a rate, so it needs a VAT class. An "
                            + "exempt line has no rate to charge itself at.");
        }
    }

    /** Stock: a quantity at a unit price, destined for GR/IR clearing. */
    public static NewPurchaseInvoiceLine inventory(
            long productId, Quantity quantity, UnitCost unitPrice, long vatClassId) {
        return new NewPurchaseInvoiceLine(PurchaseLineType.INVENTORY, productId, quantity, unitPrice,
                null, null, null, vatClassId, null, false, List.of());
    }

    /** Anything that is not stock: an amount against an account the operator named. */
    public static NewPurchaseInvoiceLine expense(
            long expenseAccountId, Money amount, long vatClassId) {
        return new NewPurchaseInvoiceLine(PurchaseLineType.EXPENSE, null, null, null,
                expenseAccountId, amount, null, vatClassId, null, false, List.of());
    }

    /** The same line, exempt under a named article instead of charged at a rate. */
    public NewPurchaseInvoiceLine exemptUnder(long reasonId) {
        return new NewPurchaseInvoiceLine(type, productId, quantity, unitPrice, expenseAccountId,
                amount, description, null, reasonId, false, matches);
    }

    /**
     * The same line, self-assessed under reverse charge — an intra-EU B2B acquisition.
     *
     * <p>Q14 settled that this needs no new structure: it posts input VAT and output VAT for the same
     * class and base in one ordinary entry, netting to zero in cash while leaving both figures
     * separately declarable.
     */
    public NewPurchaseInvoiceLine reverseCharged() {
        return new NewPurchaseInvoiceLine(type, productId, quantity, unitPrice, expenseAccountId,
                amount, description, vatClassId, vatExemptionReasonId, true, matches);
    }

    public NewPurchaseInvoiceLine describedAs(String lineDescription) {
        return new NewPurchaseInvoiceLine(type, productId, quantity, unitPrice, expenseAccountId,
                amount, lineDescription, vatClassId, vatExemptionReasonId, reverseCharge, matches);
    }

    /** The same line paying for stock that has already been received. */
    public NewPurchaseInvoiceLine matching(GoodsReceiptMatch... receiptMatches) {
        return new NewPurchaseInvoiceLine(type, productId, quantity, unitPrice, expenseAccountId,
                amount, description, vatClassId, vatExemptionReasonId, reverseCharge,
                List.of(receiptMatches));
    }

    public boolean isInventory() {
        return type == PurchaseLineType.INVENTORY;
    }

    public boolean isExempt() {
        return vatExemptionReasonId != null;
    }

    public Optional<Long> vatClass() {
        return Optional.ofNullable(vatClassId);
    }

    /**
     * The net amount of this line, unrounded.
     *
     * <p>Unrounded on purpose: an inventory line's net is a six-decimal unit price times a six-decimal
     * quantity, and the single rounding into a postable amount happens once, in the service, with the
     * mode read from settings. Rounding here would put a second one in front of it.
     */
    public BigDecimal netExactly() {
        return isInventory() ? unitPrice.extendExactly(quantity) : amount.amount();
    }

    /** The currency this line is denominated in. ADR 0005: an invoice never spans two. */
    public Currency currency() {
        return isInventory() ? unitPrice.currency() : amount.currency();
    }
}
