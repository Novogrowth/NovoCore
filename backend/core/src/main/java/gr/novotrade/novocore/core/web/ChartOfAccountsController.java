package gr.novotrade.novocore.core.web;

import gr.novotrade.novocore.core.api.account.AccountGroupView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.security.CurrentUser;
import gr.novotrade.novocore.core.api.security.Section;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only access to the chart of accounts. One endpoint.
 *
 * <p><strong>Why this exists.</strong> Not to start the frontend API — it is here to make the
 * {@code ..core.web..} architecture rule real. That rule forbids a controller from reaching past
 * {@code core-api} into a repository, and until now it had no classes to check, so it passed
 * while proving nothing. With one controller in place the rule is load-bearing, and the pattern
 * every later controller follows is established while there is one to get right rather than ten
 * to retrofit.
 *
 * <p>Note what this class does <em>not</em> have: no repository, no entity, no
 * {@code @Transactional}, no mapping code. It has a service interface and a permission check. The
 * only NovoCore packages it can see are under {@code ..core.api..}; {@code Account} and
 * {@code AccountRepository} are package-private in {@code ..core.account..} and therefore not
 * reachable from here even by accident.
 *
 * <p>The response type is the core's own {@link AccountGroupView}, not a separate web DTO. That is
 * a deliberate choice for a read-only projection that is already shaped for the outside world; a
 * parallel set of web records would be a second thing to keep in step for no gain. It will stop
 * being the right choice the first time a response needs a shape the core does not have.
 */
@RestController
@RequestMapping(path = "/api/chart-of-accounts", produces = MediaType.APPLICATION_JSON_VALUE)
class ChartOfAccountsController {

    private final ChartOfAccountsService chartOfAccounts;
    private final CurrentUser currentUser;

    ChartOfAccountsController(ChartOfAccountsService chartOfAccounts, CurrentUser currentUser) {
        this.chartOfAccounts = chartOfAccounts;
        this.currentUser = currentUser;
    }

    /**
     * The whole chart: groups in display order, each with its accounts in display order.
     *
     * <p>Authentication is already guaranteed by the filter chain, so the check here is
     * <em>authorisation</em>: does this user's role include the chart of accounts? For
     * Remote/Order Staff it does not, and this is what refuses them.
     *
     * <p>The check is an explicit call rather than an annotation on purpose. A section is a typed
     * enum value, so this cannot be misspelled, where the string inside a
     * {@code @PreAuthorize("...")} can be — and a misspelled expression that fails open is the
     * worst outcome available. With many controllers this should become a shared interceptor; at
     * one, explicit is clearer and there is nothing to share yet.
     */
    @GetMapping
    List<AccountGroupView> chart() {
        currentUser.require().requireView(Section.CHART_OF_ACCOUNTS);
        return chartOfAccounts.chart();
    }
}
