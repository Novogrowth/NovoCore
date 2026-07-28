package gr.novotrade.novocore.core.purchasing;

import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceLineView;
import gr.novotrade.novocore.core.api.shared.Quantity;

/**
 * Builds a {@link PurchaseInvoiceLineView} from a line.
 *
 * <p>Extracted in step 10 because a second service in this slice needed the same projection:
 * {@code FreightAllocationService.linesAwaitingAllocation} returns invoice lines, and a private copy
 * of the mapping in each service is exactly the kind of intra-component duplication that
 * {@code CLAUDE.md}'s code-quality section warns about — two projections of one row, free to disagree
 * the first time a field is added to either.
 *
 * <p>Not a Spring bean and not stateful: the caller supplies what it had to look up anyway (the SKU,
 * and how much of the line has been delivered), because those come from services this class has no
 * business holding.
 */
final class PurchaseInvoiceLineViews {

    private PurchaseInvoiceLineViews() {
    }

    static PurchaseInvoiceLineView of(
            PurchaseInvoiceLine line, String productSku, Quantity matchedQuantity) {
        Quantity open = line.isInventory()
                ? line.getQuantity().minus(matchedQuantity)
                : Quantity.ZERO;

        return new PurchaseInvoiceLineView(
                line.getId(),
                line.getLineNumber(),
                line.getLineType(),
                line.getProductId(),
                productSku,
                line.getQuantity(),
                line.getUnitPrice(),
                line.getExpenseAccountId(),
                line.getNetAmount(),
                line.getVatClassId(),
                line.getVatAmount(),
                line.getVatExemptionReasonId(),
                line.isReverseCharge(),
                line.getDescription(),
                matchedQuantity,
                open);
    }

    /**
     * An expense line: it names no product and there is no delivery for it to be matched against, so
     * both quantities are zero rather than absent.
     */
    static PurchaseInvoiceLineView expense(PurchaseInvoiceLine line) {
        return of(line, null, Quantity.ZERO);
    }
}
