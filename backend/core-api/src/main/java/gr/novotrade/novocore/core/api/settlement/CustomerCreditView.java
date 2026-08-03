package gr.novotrade.novocore.core.api.settlement;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Unallocated customer credit as a standalone document. <strong>Q16, answered.</strong>
 *
 * <p>Not a bare Accounts receivable balance: brief §6 treats every financial event as a trackable
 * document with its own lifecycle, and a credit balance a customer can spend is one. It has its own
 * open amount, it is allocated against later invoices like any other source, and it is queryable per
 * customer without inferring anything from the sign of a control-account balance.
 *
 * <p><strong>It posts nothing.</strong> The money is already in Accounts receivable — the receipt
 * that overpaid put it there. This document says whose it is and that it is available, which is the
 * open-item layer's job; giving it a journal entry would debit and credit AR for the same amount.
 *
 * @param openAmount what is left of it, computed from the allocations against it. Not stored, for the
 *     reason no balance in NovoCore is stored.
 */
public record CustomerCreditView(
        long id,
        long customerId,
        @Mandatory String customerName,
        long settlementId,
        @Mandatory LocalDate creditDate,
        @Mandatory Money amount,
        @Mandatory Money openAmount,
        String description) {

    public CustomerCreditView {
        Objects.requireNonNull(customerName, "customerName");
        Objects.requireNonNull(creditDate, "creditDate");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(openAmount, "openAmount");
    }

    /** True when none of it has been spent yet. */
    public boolean isUntouched() {
        return openAmount.equals(amount);
    }

    public boolean isExhausted() {
        return openAmount.isZero();
    }
}
