package gr.novotrade.novocore.core.api.purchasing;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Request to record a physical delivery — brief §6's verification step, and ADR 0004's inventory
 * event.
 *
 * <p>Every line becomes one inventory lot, and the whole receipt posts one entry: debit Inventory per
 * lot, credit GR/IR clearing against the supplier. The supplier is on the receipt rather than on each
 * line because a delivery comes from one supplier — a van carrying two suppliers' goods is two
 * deliveries that happened to share a vehicle, and recording them as one would put both into a single
 * GR/IR position that neither supplier's invoice can clear.
 *
 * @param deliveryNoteNumber the supplier's own dispatch note reference, if the delivery came with one.
 *     Optional, because plenty do not, and refusing the receipt for want of a piece of paper would
 *     leave real stock unrecorded. Not unique: unlike an invoice number this is not something the
 *     accounts are reconciled against.
 * @param receiptDate when the goods physically arrived, which is also the accounting date. Brief §6
 *     makes the whole point of this document that it is <em>not</em> the invoice date.
 */
public record NewGoodsReceipt(
        long supplierId,
        String deliveryNoteNumber,
        @Mandatory LocalDate receiptDate,
        String description,
        @Mandatory List<NewGoodsReceiptLine> lines) {

    public NewGoodsReceipt {
        Objects.requireNonNull(receiptDate, "receiptDate");
        Objects.requireNonNull(lines, "lines");
        lines = List.copyOf(lines);

        if (supplierId <= 0) {
            throw new IllegalArgumentException(
                    "supplierId must be a positive NovoCore id, got " + supplierId);
        }
        deliveryNoteNumber =
                (deliveryNoteNumber == null || deliveryNoteNumber.isBlank())
                        ? null : deliveryNoteNumber.trim();
        description = (description == null || description.isBlank()) ? null : description.trim();

        if (lines.isEmpty()) {
            throw new IllegalArgumentException(
                    "A goods receipt with no lines received nothing. A delivery that turned out to be "
                            + "empty is a conversation with the supplier, not a document.");
        }
    }

    public static NewGoodsReceipt of(
            long supplierId, LocalDate receiptDate, List<NewGoodsReceiptLine> lines) {
        return new NewGoodsReceipt(supplierId, null, receiptDate, null, lines);
    }

    public NewGoodsReceipt withDeliveryNote(String number) {
        return new NewGoodsReceipt(supplierId, number, receiptDate, description, lines);
    }

    public NewGoodsReceipt describedAs(String receiptDescription) {
        return new NewGoodsReceipt(
                supplierId, deliveryNoteNumber, receiptDate, receiptDescription, lines);
    }
}
