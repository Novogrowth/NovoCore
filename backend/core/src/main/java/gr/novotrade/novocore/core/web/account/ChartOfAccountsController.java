package gr.novotrade.novocore.core.web.account;

import gr.novotrade.novocore.core.api.account.AccountGroupView;
import gr.novotrade.novocore.core.api.account.AccountKind;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.account.NewAccount;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.SubLedgerType;
import gr.novotrade.novocore.core.api.shared.InvalidInputException;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.api.shared.Required;
import gr.novotrade.novocore.core.web.Requires;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The chart of accounts, its groups, and their ordering.
 *
 * <p>The pattern every controller in this codebase follows was set here in step 4b and is unchanged:
 * <strong>no repository, no entity, no {@code @Transactional}, no mapping code</strong> — a service
 * interface and a permission declaration. The only NovoCore packages reachable from here are under
 * {@code ..core.api..}; {@code Account} and {@code AccountRepository} are package-private in
 * {@code ..core.account..} and an architecture rule refuses the dependency even if they were not.
 *
 * <p>What changed in step 14 is the permission check. Step 4b called
 * {@code requireView(Section.CHART_OF_ACCOUNTS)} inline and its javadoc said that with many
 * controllers it should become a shared interceptor. It now is: {@link Requires} on the class covers
 * every handler, and mutating handlers raise the level to {@link AccessLevel#FULL}.
 */
@RestController
@Requires(section = Section.CHART_OF_ACCOUNTS)
class ChartOfAccountsController {

    private final ChartOfAccountsService chartOfAccounts;

    ChartOfAccountsController(ChartOfAccountsService chartOfAccounts) {
        this.chartOfAccounts = chartOfAccounts;
    }

    // -------------------------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------------------------

    /** The whole chart: groups in display order, each with its accounts in display order. */
    @GetMapping(path = "/api/chart-of-accounts", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<AccountGroupView> chart() {
        return ListResponse.of(chartOfAccounts.chart());
    }

    /** Groups alone, without their accounts — for a picker on an account form. */
    @GetMapping(path = "/api/account-groups", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<AccountGroupView> groups() {
        return ListResponse.of(chartOfAccounts.groups());
    }

    /**
     * Accounts as a flat list, filtered.
     *
     * <p>The filters are the service's own named queries rather than a general query grammar, so
     * every combination this answers is one the core already knows how to answer. {@code kind} and
     * {@code subLedgerType} imply active-only because the service's methods do; asking for both at
     * once is refused rather than silently honouring one, since guessing which the caller meant is
     * exactly what {@code CLAUDE.md} rule 7 forbids.
     */
    @GetMapping(path = "/api/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<AccountView> accounts(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) AccountKind kind,
            @RequestParam(required = false) SubLedgerType subLedgerType,
            @RequestParam(required = false) Boolean expectedToClear) {

        int filters = (kind == null ? 0 : 1)
                + (subLedgerType == null ? 0 : 1)
                + (Boolean.TRUE.equals(expectedToClear) ? 1 : 0);
        if (filters > 1) {
            throw new InvalidInputException(
                    "kind, subLedgerType and expectedToClear are alternative filters; name one.");
        }

        if (kind != null) {
            return ListResponse.of(chartOfAccounts.activeAccountsOfKind(kind));
        }
        if (subLedgerType != null) {
            return ListResponse.of(chartOfAccounts.activeControlAccountsFor(subLedgerType));
        }
        if (Boolean.TRUE.equals(expectedToClear)) {
            return ListResponse.of(chartOfAccounts.activeAccountsExpectedToClear());
        }
        return ListResponse.of(Boolean.TRUE.equals(active)
                ? chartOfAccounts.activeAccounts()
                : chartOfAccounts.allAccounts());
    }

    /** The accounts a settlement may debit or credit (brief §6's method-to-account mapping). */
    @GetMapping(path = "/api/accounts/settlement-targets", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<AccountView> settlementTargets() {
        return ListResponse.of(chartOfAccounts.activeSettlementTargets());
    }

    @GetMapping(path = "/api/accounts/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    AccountView account(@PathVariable long id) {
        return chartOfAccounts.requireAccount(id);
    }

    // -------------------------------------------------------------------------------------------
    // Changing
    // -------------------------------------------------------------------------------------------

    @PostMapping(path = "/api/accounts",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.CHART_OF_ACCOUNTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    AccountView createAccount(@RequestBody NewAccount request) {
        return chartOfAccounts.createAccount(request);
    }

    @PostMapping(path = "/api/account-groups",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.CHART_OF_ACCOUNTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    AccountGroupView createGroup(@RequestBody NameRequest request) {
        return chartOfAccounts.createGroup(request.name());
    }

    @PatchMapping(path = "/api/accounts/{id}/name",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.CHART_OF_ACCOUNTS, level = AccessLevel.FULL)
    AccountView renameAccount(@PathVariable long id, @RequestBody NameRequest request) {
        return chartOfAccounts.renameAccount(id, request.name());
    }

    @PatchMapping(path = "/api/account-groups/{id}/name",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.CHART_OF_ACCOUNTS, level = AccessLevel.FULL)
    AccountGroupView renameGroup(@PathVariable long id, @RequestBody NameRequest request) {
        return chartOfAccounts.renameGroup(id, request.name());
    }

    /**
     * Retires an account. <strong>Answers with the balance it was left carrying</strong>, rather
     * than 204.
     *
     * <p>Deactivation warns rather than refuses when an account still has a balance — retiring a
     * populated account before a rearrangement is legitimate, and the money does not vanish, it
     * simply stops being added to. The service returns that warning as its return value
     * specifically so a caller cannot fail to receive it, and a 204 here would throw it away at the
     * last possible moment.
     */
    @PostMapping(path = "/api/accounts/{id}/deactivate", produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.CHART_OF_ACCOUNTS, level = AccessLevel.FULL)
    AccountDeactivated deactivateAccount(@PathVariable long id) {
        return new AccountDeactivated(chartOfAccounts.deactivate(id).orElse(null));
    }

    @PostMapping(path = "/api/accounts/{id}/reactivate")
    @Requires(section = Section.CHART_OF_ACCOUNTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivateAccount(@PathVariable long id) {
        chartOfAccounts.reactivate(id);
    }

    /**
     * Reorders the accounts inside one group.
     *
     * <p>{@code PUT}, not {@code PATCH}, and the body must name <strong>every</strong> member of the
     * group exactly once. The service refuses a partial list rather than leaving the remainder in an
     * order nobody chose ({@code CLAUDE.md} rule 7) — so this replaces the whole ordering, which is
     * what {@code PUT} means.
     */
    @PutMapping(path = "/api/account-groups/{groupId}/account-order",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.CHART_OF_ACCOUNTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reorderAccounts(@PathVariable long groupId, @RequestBody OrderRequest request) {
        chartOfAccounts.reorderAccountsWithinGroup(groupId, request.idsInOrder());
    }

    /** Reorders the groups themselves. Same whole-list rule as the accounts within one. */
    @PutMapping(path = "/api/account-groups/order", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.CHART_OF_ACCOUNTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reorderGroups(@RequestBody OrderRequest request) {
        chartOfAccounts.reorderGroups(request.idsInOrder());
    }

    // -------------------------------------------------------------------------------------------

    /** A rename or a create-by-name. */
    record NameRequest(String name) {
    }

    /** A complete ordering. Partial lists are refused by the service, not tolerated here. */
    record OrderRequest(List<Long> idsInOrder) {

        OrderRequest {
            Required.field(idsInOrder, "idsInOrder");
        }
    }

    /**
     * @param residualBalance what the account was still carrying, or null when it was zero or had
     *     no activity. Not a failure — the account was deactivated either way.
     */
    record AccountDeactivated(Money residualBalance) {
    }
}
