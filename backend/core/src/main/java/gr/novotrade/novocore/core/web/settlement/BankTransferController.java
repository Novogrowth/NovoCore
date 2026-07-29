package gr.novotrade.novocore.core.web.settlement;

import gr.novotrade.novocore.core.api.banking.BankTransferService;
import gr.novotrade.novocore.core.api.banking.BankTransferView;
import gr.novotrade.novocore.core.api.banking.NewBankTransfer;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.web.InvalidRequestException;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.web.Requires;
import gr.novotrade.novocore.core.web.ReversalCommand;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transfers between our own accounts.
 *
 * <p><strong>Two lines and no third account.</strong> Brief §4 drops Manager's "Inter Account
 * Transfers", which Manager placed under Equity — the error the brief corrects. A transfer between
 * two asset accounts is exactly that: one credited, one debited.
 *
 * <p>Under {@code SETTLEMENTS} rather than a section of its own, because reading what moved between
 * bank accounts is the same job as reading what was received and paid.
 */
@RestController
@Requires(section = Section.SETTLEMENTS)
class BankTransferController {

    private final BankTransferService transfers;

    BankTransferController(BankTransferService transfers) {
        this.transfers = transfers;
    }

    @GetMapping(path = "/api/bank-transfers", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<BankTransferView> transfers(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to) {

        if (accountId != null) {
            return ListResponse.of(transfers.involving(accountId));
        }
        if (from == null || to == null) {
            throw new InvalidRequestException(
                    "a date range needs 'from' and 'to', or name an accountId instead");
        }
        return ListResponse.of(transfers.between(from, to));
    }

    @GetMapping(path = "/api/bank-transfers/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    BankTransferView transfer(@PathVariable long id) {
        return transfers.require(id);
    }

    @PostMapping(path = "/api/bank-transfers",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SETTLEMENTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    BankTransferView record(@RequestBody NewBankTransfer request) {
        return transfers.record(request);
    }

    @PostMapping(path = "/api/bank-transfers/{id}/reversal",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SETTLEMENTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    BankTransferView reverse(
            @PathVariable long id,
            @RequestBody ReversalCommand request) {
        return transfers.reverse(id, request.reversalDate(), request.reason());
    }
}
