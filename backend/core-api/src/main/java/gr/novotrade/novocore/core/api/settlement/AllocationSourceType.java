package gr.novotrade.novocore.core.api.settlement;

/**
 * What supplied the money in an allocation.
 *
 * <p>Three kinds, because there genuinely are three: cash that moved, a credit note being set against
 * an invoice instead of refunded, and customer credit left over from an earlier overpayment. Only the
 * first of them posted anything — see {@link AllocationView}.
 */
public enum AllocationSourceType {

    /** A Receipt or a Payment. The one source that moved money through one of our accounts. */
    SETTLEMENT,

    /** A credit note set against an invoice rather than refunded. Both ends are Accounts receivable. */
    CREDIT_NOTE,

    /** Q16's standalone credit document, spending an earlier overpayment. */
    CUSTOMER_CREDIT
}
