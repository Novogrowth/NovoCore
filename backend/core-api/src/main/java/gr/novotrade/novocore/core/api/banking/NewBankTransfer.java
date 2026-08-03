package gr.novotrade.novocore.core.api.banking;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Request to move money between two of our own accounts.
 *
 * <p><strong>There is no "Inter Account Transfers" account, deliberately.</strong> Brief §4 drops it
 * — Manager had one, under Equity, which is the error the brief corrects. A transfer is two
 * asset-account lines and nothing more, so the entry needs no third account to pass through.
 */
public record NewBankTransfer(
        long fromAccountId,
        long toAccountId,
        @Mandatory LocalDate transferDate,
        @Mandatory Money amount,
        String reference,
        String description) {

    public NewBankTransfer {
        Objects.requireNonNull(transferDate, "transferDate");
        Objects.requireNonNull(amount, "amount");
        reference = (reference == null || reference.isBlank()) ? null : reference.trim();
        description = (description == null || description.isBlank()) ? null : description.trim();

        if (fromAccountId <= 0 || toAccountId <= 0) {
            throw new IllegalArgumentException(
                    "Both accounts must be positive NovoCore ids, got " + fromAccountId + " and "
                            + toAccountId + ".");
        }
        if (fromAccountId == toAccountId) {
            throw new IllegalArgumentException(
                    "A transfer from an account to itself is two lines that cancel: it balances, it "
                            + "states nothing, and it appears on that account's ledger twice.");
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException(
                    "Transfer amount " + amount + " is not positive. Which way the money went is said "
                            + "by which account is which, not by the sign.");
        }
    }

    public static NewBankTransfer of(
            long fromAccountId, long toAccountId, LocalDate transferDate, Money amount) {
        return new NewBankTransfer(fromAccountId, toAccountId, transferDate, amount, null, null);
    }

    public NewBankTransfer referenced(String bankReference) {
        return new NewBankTransfer(
                fromAccountId, toAccountId, transferDate, amount, bankReference, description);
    }

    public NewBankTransfer describedAs(String transferDescription) {
        return new NewBankTransfer(
                fromAccountId, toAccountId, transferDate, amount, reference, transferDescription);
    }
}
