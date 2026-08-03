package gr.novotrade.novocore.core.api.codification;

/**
 * Annex 8.1's grouping of the myDATA invoice types.
 *
 * <p>⭐ <strong>This enum is why there is one codification table rather than two.</strong> An
 * earlier design split the XSD's single 55-value {@code InvoiceType} enumeration into a sales table
 * and a purchase table, using these headings as the knife. Six codes — {@code 17.1}–{@code 17.6},
 * the entity's own adjusting entries — then belonged to neither, and the open question was whether
 * to omit them, add a third table, or carry a discriminator.
 *
 * <p>The question was the wrong shape. With the group as an <em>attribute</em> of one table, those
 * six are ordinary rows and {@code 28 + 6 + 6 + 9 + 6 = 55} still reconciles exactly against the
 * XSD. Nothing was omitted, nothing invented, and no third table exists.
 *
 * <p>⚠️ <strong>The sales/purchase split is NOVOCORE'S, not AADE's.</strong> The XSD has one
 * enumeration covering both directions; the split comes from reading these headings. That is worth
 * knowing before anyone treats "34 sales codes" as something the authority published.
 */
public enum AadeInvoiceGroup {

    /**
     * Αντικριζόμενα Παραστατικά Εκδότη ημεδαπής / αλλοδαπής — 28 codes.
     *
     * <p>Documents <strong>we issue</strong> that the counterparty also reports, so AADE matches
     * the two sides. Sales.
     */
    ISSUER_MATCHED(true),

    /**
     * Μη Αντικριζόμενα Παραστατικά Εκδότη → Παραστατικά Λιανικής — 6 codes.
     *
     * <p>Retail documents <strong>we issue</strong> with no counterparty report to match against.
     * Sales.
     */
    ISSUER_UNMATCHED(true),

    /**
     * Μη Αντικριζόμενα Παραστατικά Λήπτη → Λήψη Παραστατικών Λιανικής — 6 codes.
     *
     * <p>Retail documents <strong>we receive</strong>. Purchase.
     */
    RECIPIENT_UNMATCHED(false),

    /**
     * Αντικριζόμενα Παραστατικά Λήπτη ημεδαπής / αλλοδαπής — 9 codes.
     *
     * <p>Documents <strong>we receive</strong> that the issuer also reports. Purchase.
     */
    RECIPIENT_MATCHED(false),

    /**
     * Εγγραφές Τακτοποίησης Εσόδων-Εξόδων → Εγγραφές Οντότητας — 6 codes.
     *
     * <p>⚠️ <strong>Neither issued nor received.</strong> Payroll, depreciation and the four
     * income/expense adjusting entries are the entity's own journal entries, with no counterparty
     * at all. {@link #issuedByUs()} is deliberately not answerable for this group, which is why it
     * throws rather than returning a default: a caller that has not thought about this group would
     * otherwise silently file six statutory codes on the wrong side.
     */
    ENTITY_ADJUSTING(null);

    private final Boolean issuedByUs;

    AadeInvoiceGroup(Boolean issuedByUs) {
        this.issuedByUs = issuedByUs;
    }

    /**
     * True for the groups Novocore treats as sales, false for purchase.
     *
     * @throws IllegalStateException for {@link #ENTITY_ADJUSTING}, which is neither. This is a
     *     programming error and not a caller's mistake — nothing on the HTTP surface reaches it,
     *     and code asking a self-issued journal entry which party issued it has a bug rather than
     *     bad input.
     */
    public boolean issuedByUs() {
        if (issuedByUs == null) {
            throw new IllegalStateException(
                    name() + " is the entity's own adjusting entries. They are neither issued to "
                            + "nor received from a counterparty, so there is no sales/purchase "
                            + "side to report. Handle this group explicitly.");
        }
        return issuedByUs;
    }

    /** Whether {@link #issuedByUs()} has an answer, so a caller can ask before asking. */
    public boolean hasCounterparty() {
        return issuedByUs != null;
    }
}
