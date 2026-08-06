package gr.novotrade.novocore.core.sales;

import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.codification.AadePaymentMethodService;
import gr.novotrade.novocore.core.api.codification.AadePaymentMethodView;
import gr.novotrade.novocore.core.api.sales.InvalidPaymentMethodException;
import gr.novotrade.novocore.core.api.sales.NewPaymentMethod;
import gr.novotrade.novocore.core.api.sales.PaymentMethodNotFoundException;
import gr.novotrade.novocore.core.api.sales.PaymentMethodService;
import gr.novotrade.novocore.core.api.sales.PaymentMethodView;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaymentMethodServiceImpl implements PaymentMethodService {

    private static final String ENTITY_TYPE = "PaymentMethod";

    /**
     * Annex 8.12's article for Μετρητά, and the reason it is a constant here rather than a column on
     * the codification.
     *
     * <p>The cash limit is <strong>Greek law as NovoCore applies it</strong> (N. 5301/2026), not an
     * attribute AADE published — so the codification stays a faithful copy of the artefact and the
     * rule lives in our own code.
     *
     * <p>✅ <strong>The code was CONFIRMED, not assumed.</strong> Read from a rasterised page of the
     * myDATA v2.0.1 ERP specification (PDF page 105, document page 104) on 2026-08-06, agreeing with
     * an independent rasterised read made in R1a. ⚠️ It could not be taken from the XSD:
     * {@code paymentMethods-v2.0.1.xsd} defines no code list, and the type is an {@code xs:int} range
     * of 1 to 8 — so {@code CLAUDE.md}'s "codes from the XSD" rule has no safe side for this annex.
     * See {@code V37} and {@code docs/aade/v2.0.1/README.md} section 5.
     *
     * <p>⚠️ <strong>This is the RETAIL threshold only.</strong> The B2B rule is the same figure NET
     * plus VAT, and it needs a retail/B2B distinction the model does not have — roadmap row
     * <strong>C2</strong>, deliberately not R4's.
     */
    static final int CASH_ARTICLE_CODE = 3;

    private final PaymentMethodRepository repository;
    private final AadePaymentMethodService articles;
    private final ChartOfAccountsService chartOfAccounts;
    private final AuditLogService auditLog;

    PaymentMethodServiceImpl(PaymentMethodRepository repository, AadePaymentMethodService articles,
            ChartOfAccountsService chartOfAccounts, AuditLogService auditLog) {
        this.repository = repository;
        this.articles = articles;
        this.chartOfAccounts = chartOfAccounts;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodView> all() {
        return toViews(repository.findAllByOrderBySortCodeAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodView> active() {
        return toViews(repository.findByActiveTrueOrderBySortCodeAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentMethodView> find(long id) {
        return repository.findById(id).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentMethodView require(long id) {
        return find(id).orElseThrow(() -> new PaymentMethodNotFoundException(id));
    }

    @Override
    @Transactional
    public PaymentMethodView create(NewPaymentMethod request) {
        String abbreviation = request.abbreviation();
        requireAbbreviationFree(abbreviation, null);
        // ⚠️ THE ONE ALLOCATOR. Null means "append at the end"; see NewPaymentMethod.sortCode for
        // why this lives here rather than in each caller.
        int sortCode = request.sortCode() == null ? nextSortCode() : request.sortCode();
        requireSortCodeFree(sortCode, null);

        AadePaymentMethodView article = requireUsableArticle(request.aadePaymentMethodId());
        AccountView account = requirePermittedAccount(request.accountId());

        PaymentMethod saved = repository.save(new PaymentMethod(abbreviation, request.description(),
                article.id(), account.id(), sortCode));

        auditLog.record("payment-method.created", ENTITY_TYPE, String.valueOf(saved.getId()),
                Map.of("abbreviation", abbreviation,
                        "description", saved.getDescription(),
                        "aadePaymentMethodCode", String.valueOf(article.code()),
                        "account", account.name()));

        return toView(saved);
    }

    @Override
    @Transactional
    public PaymentMethodView changeAbbreviation(long id, String abbreviation) {
        PaymentMethod method = load(id);
        String corrected = requireText(abbreviation, "An abbreviation");
        if (corrected.equals(method.getAbbreviation())) {
            return toView(method);
        }
        requireNothingRecorded(method, "abbreviation");
        requireAbbreviationFree(corrected, id);

        String previous = method.getAbbreviation();
        method.changeAbbreviation(corrected);
        auditLog.record("payment-method.abbreviation-changed", ENTITY_TYPE, String.valueOf(id),
                Map.of("previousAbbreviation", previous, "abbreviation", corrected));
        return toView(method);
    }

    @Override
    @Transactional
    public PaymentMethodView describe(long id, String description) {
        PaymentMethod method = load(id);
        String corrected = requireText(description, "A description");
        if (corrected.equals(method.getDescription())) {
            return toView(method);
        }
        requireNothingRecorded(method, "description");

        String previous = method.getDescription();
        method.describe(corrected);
        auditLog.record("payment-method.described", ENTITY_TYPE, String.valueOf(id),
                Map.of("previousDescription", previous, "description", corrected));
        return toView(method);
    }

    @Override
    @Transactional
    public PaymentMethodView changeArticle(long id, long aadePaymentMethodId) {
        PaymentMethod method = load(id);
        if (method.getAadePaymentMethodId() == aadePaymentMethodId) {
            return toView(method);
        }
        requireNothingRecorded(method, "AADE payment-method article");
        AadePaymentMethodView article = requireUsableArticle(aadePaymentMethodId);

        method.changeArticle(article.id());
        auditLog.record("payment-method.article-changed", ENTITY_TYPE, String.valueOf(id),
                Map.of("aadePaymentMethodCode", String.valueOf(article.code())));
        return toView(method);
    }

    @Override
    @Transactional
    public PaymentMethodView changeAccount(long id, long accountId) {
        PaymentMethod method = load(id);
        if (method.getAccountId() == accountId) {
            return toView(method);
        }
        requireNothingRecorded(method, "account");
        AccountView account = requirePermittedAccount(accountId);

        method.changeAccount(account.id());
        auditLog.record("payment-method.account-changed", ENTITY_TYPE, String.valueOf(id),
                Map.of("account", account.name()));
        return toView(method);
    }

    /**
     * Reorders it.
     *
     * <p>⚠️ <strong>Deliberately NOT subject to the in-use freeze</strong> — reordering is normal and
     * a sort code appears on no document. The same exemption {@code V34}'s four tables carry, and the
     * reason R2c's 2b mattered: a value settable only once is unusable for the purpose that argument
     * assigns it.
     */
    @Override
    @Transactional
    public PaymentMethodView changeSortCode(long id, int sortCode) {
        PaymentMethod method = load(id);
        if (method.getSortCode() == sortCode) {
            return toView(method);
        }
        requireSortCodeFree(sortCode, id);

        int previous = method.getSortCode();
        method.changeSortCode(sortCode);
        auditLog.record("payment-method.reordered", ENTITY_TYPE, String.valueOf(id),
                Map.of("previousSortCode", String.valueOf(previous),
                        "sortCode", String.valueOf(sortCode)));
        return toView(method);
    }

    @Override
    @Transactional
    public void deactivate(long id) {
        PaymentMethod method = load(id);
        if (!method.isActive()) {
            return;
        }
        method.setActive(false);
        auditLog.record("payment-method.deactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("abbreviation", method.getAbbreviation()));
    }

    @Override
    @Transactional
    public void reactivate(long id) {
        PaymentMethod method = load(id);
        if (method.isActive()) {
            return;
        }
        method.setActive(true);
        auditLog.record("payment-method.reactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("abbreviation", method.getAbbreviation()));
    }

    // -------------------------------------------------------------------------------------------
    // Guards
    // -------------------------------------------------------------------------------------------

    /** The one place a sort code is invented. Ten-spaced, so a row can be slid between two. */
    private int nextSortCode() {
        return repository.findAllByOrderBySortCodeAsc().stream()
                .mapToInt(PaymentMethod::getSortCode).max().orElse(0) + 10;
    }

    private PaymentMethod load(long id) {
        return repository.findById(id).orElseThrow(() -> new PaymentMethodNotFoundException(id));
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new InvalidPaymentMethodException(what + " must not be blank.");
        }
        return value.trim();
    }

    private void requireAbbreviationFree(String abbreviation, Long selfId) {
        repository.findByAbbreviationIgnoreCase(abbreviation).ifPresent(existing -> {
            if (selfId == null || !existing.getId().equals(selfId)) {
                throw new InvalidPaymentMethodException(
                        "Abbreviation \"" + abbreviation + "\" is already used by payment method \""
                                + existing.getDescription() + "\". An abbreviation is how an operator "
                                + "tells two methods apart in a picker, so it must be unique.");
            }
        });
    }

    private void requireSortCodeFree(int sortCode, Long selfId) {
        repository.findAllByOrderBySortCodeAsc().stream()
                .filter(existing -> existing.getSortCode() == sortCode)
                .filter(existing -> selfId == null || !existing.getId().equals(selfId))
                .findFirst()
                .ifPresent(existing -> {
                    throw new InvalidPaymentMethodException(
                            "Sort code " + sortCode + " is already used by \""
                                    + existing.getDescription() + "\". Sort codes are unique so the "
                                    + "list order is deterministic.");
                });
    }

    private AadePaymentMethodView requireUsableArticle(long articleId) {
        AadePaymentMethodView article = articles.require(articleId);
        if (!article.active()) {
            throw new InvalidPaymentMethodException(
                    "AADE payment-method article " + article.code() + " (" + article.description()
                            + ") is inactive, so it is not for new payment methods. An inactive "
                            + "article is one AADE no longer publishes, and a document declaring it "
                            + "would be transmitted under a code that no longer exists.");
        }
        return article;
    }

    /**
     * The account must be one a payment method may reconcile to — bank, cash, partner clearing, or
     * <strong>Accounts receivable</strong>.
     *
     * <p>⚠️ Refused in the service and not merely filtered in a picker, because a picker is a control
     * and this is a rule: an adapter or a direct call faces no picker at all.
     */
    private AccountView requirePermittedAccount(long accountId) {
        return chartOfAccounts.activePaymentMethodTargets().stream()
                .filter(candidate -> candidate.id() == accountId)
                .findFirst()
                .orElseThrow(() -> new InvalidPaymentMethodException(
                        "Account " + accountId + " is not one a payment method may reconcile to. "
                                + "It must be an active bank, cash or partner-clearing account — "
                                + "where the money actually lands — or Accounts receivable, for a "
                                + "method that leaves the invoice open."));
    }

    /**
     * Every field except the sort code is frozen once a document names this method.
     *
     * <p>⚠️ <strong>The account is why the freeze covers everything rather than a chosen few:</strong>
     * changing where a method posted, after it has posted, would make the ledger disagree with itself.
     */
    private void requireNothingRecorded(PaymentMethod method, String field) {
        if (repository.isNamedByARecordedDocument(method.getId())) {
            throw new InvalidPaymentMethodException(
                    "The " + field + " of payment method \"" + method.getDescription() + "\" cannot "
                            + "be changed, because sales documents have already been settled by it. "
                            + "The article decides what those documents declared to AADE and the "
                            + "account decides where they posted. Create a new method and deactivate "
                            + "this one.");
        }
    }

    // -------------------------------------------------------------------------------------------
    // Views
    // -------------------------------------------------------------------------------------------

    private List<PaymentMethodView> toViews(List<PaymentMethod> methods) {
        // One query for the whole list's in-use flags, rather than one per row.
        Set<Long> inUse = new HashSet<>(repository.idsNamedByARecordedDocument());
        return methods.stream().map(m -> toView(m, inUse.contains(m.getId()))).toList();
    }

    private PaymentMethodView toView(PaymentMethod method) {
        return toView(method, repository.isNamedByARecordedDocument(method.getId()));
    }

    private PaymentMethodView toView(PaymentMethod method, boolean inUse) {
        AadePaymentMethodView article = articles.require(method.getAadePaymentMethodId());
        AccountView account = chartOfAccounts.requireAccount(method.getAccountId());
        return new PaymentMethodView(
                method.getId(),
                method.getAbbreviation(),
                method.getDescription(),
                article.id(),
                article.code(),
                article.description(),
                account.id(),
                account.name(),
                // ⚠️ DERIVED, never stored: the money is already somewhere exactly when the account
                // it lands in is a bank, cash or partner-clearing one. Accounts receivable is not.
                account.isSettlementTarget(),
                // ⚠️ DERIVED from the article, never stored. See CASH_ARTICLE_CODE.
                article.code() == CASH_ARTICLE_CODE,
                method.getSortCode(),
                inUse,
                method.isActive());
    }
}
