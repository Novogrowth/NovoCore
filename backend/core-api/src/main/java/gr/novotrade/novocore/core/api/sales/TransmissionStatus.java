package gr.novotrade.novocore.core.api.sales;

/**
 * Where a sales document stands with AADE.
 *
 * <p>⚠️ <strong>Novocore never obtains a ΜΑΡΚ itself.</strong> Greek law requires transmission at
 * issuance, and the document receives its ΜΑΡΚ and QR code there — through Prosvasis Go today and a
 * certified Πάροχος at step 40. A sales document appears in Novocore only <em>after</em> it legally
 * exists, so this records what is known about a transmission that happened elsewhere. It is not a
 * workflow state Novocore drives.
 *
 * <p><strong>Three values and not four.</strong> A {@code FAILED} state is deliberately absent
 * because nothing in this system can produce one — nothing transmits until step 29/40. A state no
 * code path can reach reads as coverage and is not, so it will be added by whichever step first has
 * something that can put a document into it.
 */
public enum TransmissionStatus {

    /**
     * Recorded without transmission information.
     *
     * <p>The honest state for every invoice recorded before R1a, and for any document entered by
     * hand from a printed copy. It says "we do not know", which is a different thing from "it did
     * not go" — and the difference matters, because only one of those is a compliance problem.
     */
    UNKNOWN,

    /**
     * The document type does not require transmission at all.
     *
     * <p>An operational document — Προσφορά, Παραγγελία — is not a tax document, so there is no
     * ΜΑΡΚ to be missing.
     */
    NOT_REQUIRED,

    /**
     * It reached AADE and carries a ΜΑΡΚ.
     *
     * <p>⚠️ This and the presence of a ΜΑΡΚ are one fact said two ways, and the database enforces
     * that they agree: a row cannot claim transmission with no identifier to show for it, nor carry
     * an identifier while claiming it never went.
     */
    TRANSMITTED
}
