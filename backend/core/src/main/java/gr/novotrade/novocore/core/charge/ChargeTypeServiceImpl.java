package gr.novotrade.novocore.core.charge;

import gr.novotrade.novocore.core.api.account.AccountType;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.charge.ChargeTypeNotFoundException;
import gr.novotrade.novocore.core.api.charge.ChargeTypeService;
import gr.novotrade.novocore.core.api.charge.ChargeTypeView;
import gr.novotrade.novocore.core.api.charge.InvalidChargeTypeException;
import gr.novotrade.novocore.core.api.charge.NewChargeType;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatClassView;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Note how the VAT class and account references are validated: through
 * {@link VatClassService} and {@link ChartOfAccountsService}, the same published interfaces an
 * adapter would use, rather than by reaching into {@code ..core.tax} or {@code ..core.account}.
 * Those packages' entities are package-private, so this is not merely good manners — it is the
 * only route available, which is the point of ADR 0003 holding inside the core as well as at its
 * edge.
 */
@Service
class ChargeTypeServiceImpl implements ChargeTypeService {

    private static final String ENTITY_TYPE = "ChargeType";

    private final ChargeTypeRepository repository;
    private final VatClassService vatClasses;
    private final ChartOfAccountsService chartOfAccounts;
    private final AuditLogService auditLog;

    ChargeTypeServiceImpl(ChargeTypeRepository repository, VatClassService vatClasses,
            ChartOfAccountsService chartOfAccounts, AuditLogService auditLog) {
        this.repository = repository;
        this.vatClasses = vatClasses;
        this.chartOfAccounts = chartOfAccounts;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChargeTypeView> all() {
        return toViews(repository.findAllByOrderByNameAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChargeTypeView> active() {
        return toViews(repository.findByActiveTrueOrderByNameAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChargeTypeView> find(long id) {
        return repository.findById(id).map(ChargeTypeServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public ChargeTypeView require(long id) {
        return find(id).orElseThrow(() -> new ChargeTypeNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChargeTypeView> findByName(String name) {
        Objects.requireNonNull(name, "name");
        return repository.findByNameIgnoreCase(name.trim()).map(ChargeTypeServiceImpl::toView);
    }

    @Override
    @Transactional
    public ChargeTypeView create(NewChargeType request) {
        Objects.requireNonNull(request, "request");
        String name = requireText(request.name());

        if (repository.existsByNameIgnoreCase(name)) {
            throw new InvalidChargeTypeException(
                    "A charge type named '" + name + "' already exists.");
        }
        requireActiveVatClass(request.defaultVatClassId());
        requireActiveIncomeAccount(request.incomeAccountId());

        ChargeType saved = repository.save(new ChargeType(
                name, request.defaultVatClassId(), request.incomeAccountId()));

        auditLog.record("charge-type.created", ENTITY_TYPE, String.valueOf(saved.getId()), Map.of(
                "name", name,
                "defaultVatClassId", String.valueOf(request.defaultVatClassId()),
                "incomeAccountId", String.valueOf(request.incomeAccountId())));

        return toView(saved);
    }

    @Override
    @Transactional
    public ChargeTypeView rename(long id, String newName) {
        String name = requireText(newName);
        ChargeType chargeType = repository.findById(id)
                .orElseThrow(() -> new ChargeTypeNotFoundException(id));

        if (!chargeType.getName().equalsIgnoreCase(name)
                && repository.existsByNameIgnoreCase(name)) {
            throw new InvalidChargeTypeException(
                    "A charge type named '" + name + "' already exists.");
        }

        String previous = chargeType.getName();
        chargeType.rename(name);

        auditLog.record("charge-type.renamed", ENTITY_TYPE, String.valueOf(id),
                Map.of("from", previous, "to", name));

        return toView(chargeType);
    }

    @Override
    @Transactional
    public ChargeTypeView changeDefaultVatClass(long id, long vatClassId) {
        ChargeType chargeType = repository.findById(id)
                .orElseThrow(() -> new ChargeTypeNotFoundException(id));
        VatClassView vatClass = requireActiveVatClass(vatClassId);

        chargeType.changeDefaultVatClass(vatClassId);

        auditLog.record("charge-type.vat-class-changed", ENTITY_TYPE, String.valueOf(id), Map.of(
                "name", chargeType.getName(),
                "vatClass", vatClass.code()));

        return toView(chargeType);
    }

    @Override
    @Transactional
    public ChargeTypeView changeIncomeAccount(long id, long incomeAccountId) {
        ChargeType chargeType = repository.findById(id)
                .orElseThrow(() -> new ChargeTypeNotFoundException(id));
        AccountView account = requireActiveIncomeAccount(incomeAccountId);

        chargeType.changeIncomeAccount(incomeAccountId);

        auditLog.record("charge-type.income-account-changed", ENTITY_TYPE, String.valueOf(id),
                Map.of("name", chargeType.getName(), "account", account.name()));

        return toView(chargeType);
    }

    @Override
    @Transactional
    public void deactivate(long id) {
        ChargeType chargeType = repository.findById(id)
                .orElseThrow(() -> new ChargeTypeNotFoundException(id));
        if (!chargeType.isActive()) {
            return;
        }

        chargeType.setActive(false);
        auditLog.record("charge-type.deactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("name", chargeType.getName()));
    }

    @Override
    @Transactional
    public void reactivate(long id) {
        ChargeType chargeType = repository.findById(id)
                .orElseThrow(() -> new ChargeTypeNotFoundException(id));
        if (chargeType.isActive()) {
            return;
        }

        chargeType.setActive(true);
        auditLog.record("charge-type.reactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("name", chargeType.getName()));
    }

    private VatClassView requireActiveVatClass(long vatClassId) {
        VatClassView vatClass = vatClasses.find(vatClassId).orElseThrow(() ->
                new InvalidChargeTypeException(
                        "No VAT class with id " + vatClassId + "."));
        if (!vatClass.active()) {
            throw new InvalidChargeTypeException(
                    "VAT class '" + vatClass.code() + "' is inactive, so it cannot be the "
                            + "default for a charge type.");
        }
        return vatClass;
    }

    /**
     * The guard this class exists for. A delivery fee wired to the {@code Transportation costs}
     * expense account would net revenue against cost, understating both and leaving a gross
     * margin that looks plausible and is wrong. {@code CONTRA_INCOME} is refused as well: that
     * side is for sales returns, and a fee is not a return.
     */
    private AccountView requireActiveIncomeAccount(long accountId) {
        AccountView account = chartOfAccounts.findAccount(accountId).orElseThrow(() ->
                new InvalidChargeTypeException("No account with id " + accountId + "."));

        if (account.type() != AccountType.INCOME) {
            throw new InvalidChargeTypeException(
                    "Account '" + account.name() + "' is " + account.type() + ", but a charge "
                            + "type must post to an INCOME account: these fees are revenue "
                            + "charged to the customer, not a reduction of an expense we incur.");
        }
        if (!account.active()) {
            throw new InvalidChargeTypeException(
                    "Account '" + account.name() + "' is inactive.");
        }
        return account;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidChargeTypeException("Charge type name must not be blank.");
        }
        return value.trim();
    }

    private static List<ChargeTypeView> toViews(List<ChargeType> chargeTypes) {
        return chargeTypes.stream().map(ChargeTypeServiceImpl::toView).toList();
    }

    private static ChargeTypeView toView(ChargeType chargeType) {
        return new ChargeTypeView(
                chargeType.getId(),
                chargeType.getName(),
                chargeType.getDefaultVatClassId(),
                chargeType.getIncomeAccountId(),
                chargeType.isActive());
    }
}
