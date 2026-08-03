package gr.novotrade.novocore.core.api.banking;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.Objects;

/** Money moved between two of our own accounts. */
public record BankTransferView(
        long id,
        long fromAccountId,
        @Mandatory String fromAccountName,
        long toAccountId,
        @Mandatory String toAccountName,
        @Mandatory LocalDate transferDate,
        @Mandatory Money amount,
        String reference,
        String description,
        long journalEntryId) {

    public BankTransferView {
        Objects.requireNonNull(fromAccountName, "fromAccountName");
        Objects.requireNonNull(toAccountName, "toAccountName");
        Objects.requireNonNull(transferDate, "transferDate");
        Objects.requireNonNull(amount, "amount");
    }
}
