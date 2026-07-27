package gr.novotrade.novocore.core.api.shared;

import java.util.Objects;

/**
 * A reference from a journal line to the sub-ledger entity it concerns.
 *
 * <p>Required on any line posting to a Control-kind account, and its {@link #type} must match
 * that account's declared sub-ledger type (brief §6). Optional elsewhere, but genuinely used
 * on some non-Control accounts: Cost of goods sold is a Standard account whose lines still
 * carry {@link SubLedgerType#INVENTORY_LOT} references, because brief §6 requires one line per
 * lot consumed.
 *
 * <p>The referenced id is NovoCore's own internal id, never an external system's. Adapters keep
 * their own external-ID-to-core-ID mapping tables ({@code CLAUDE.md} rule 2).
 *
 * <p>Note on customer merges: brief §5 settles this as "alias forward, never rewrite history",
 * so an existing line keeps the id it was posted with even after that customer is merged into
 * another. Reports resolve aliases at read time. Nothing here rewrites a posted reference.
 */
public record SubLedgerRef(SubLedgerType type, long id) {

    public SubLedgerRef {
        Objects.requireNonNull(type, "type");
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "sub-ledger id must be a positive NovoCore id, got " + id);
        }
    }

    public static SubLedgerRef of(SubLedgerType type, long id) {
        return new SubLedgerRef(type, id);
    }

    public static SubLedgerRef customer(long customerId) {
        return new SubLedgerRef(SubLedgerType.CUSTOMER, customerId);
    }

    public static SubLedgerRef supplier(long supplierId) {
        return new SubLedgerRef(SubLedgerType.SUPPLIER, supplierId);
    }

    public static SubLedgerRef inventoryLot(long lotId) {
        return new SubLedgerRef(SubLedgerType.INVENTORY_LOT, lotId);
    }

    public static SubLedgerRef asset(long assetId) {
        return new SubLedgerRef(SubLedgerType.ASSET, assetId);
    }

    @Override
    public String toString() {
        return type + "#" + id;
    }
}
