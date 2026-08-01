package gr.novotrade.novocore.core.customer;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.customer.CustomerNotFoundException;
import gr.novotrade.novocore.core.api.customer.CustomerService;
import gr.novotrade.novocore.core.api.customer.CustomerSystemKey;
import gr.novotrade.novocore.core.api.customer.CustomerView;
import gr.novotrade.novocore.core.api.customer.InvalidCustomerException;
import gr.novotrade.novocore.core.api.customer.NewCustomer;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatClassView;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonService;
import gr.novotrade.novocore.core.api.tax.VatStatus;
import gr.novotrade.novocore.core.support.Specifications;
import gr.novotrade.novocore.core.support.TextSearch;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * VAT classes and exemption reasons are validated through their own published services, the same
 * interfaces an adapter would use — the tax slice's entities are package-private, so this is the
 * only available route rather than a matter of manners (ADR 0003).
 */
@Service
class CustomerServiceImpl implements CustomerService {

    /**
     * What a substring search looks at. The brief also names Code, which is not a column yet — the
     * customer field list is marked (draft). Queued as its own item.
     */
    private static final String[] SEARCHABLE = {"name", "vatNumber", "email", "phone"};


    private static final String ENTITY_TYPE = "Customer";

    private final CustomerRepository repository;
    private final VatClassService vatClasses;
    private final VatExemptionReasonService exemptionReasons;
    private final AuditLogService auditLog;

    CustomerServiceImpl(CustomerRepository repository, VatClassService vatClasses,
            VatExemptionReasonService exemptionReasons, AuditLogService auditLog) {
        this.repository = repository;
        this.vatClasses = vatClasses;
        this.exemptionReasons = exemptionReasons;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerView> all() {
        return toViews(repository.findAllByOrderByNameAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerView> active() {
        return toViews(repository.findByActiveTrueOrderByNameAsc());
    }

    /**
     * The VAT number is one of the searched columns, as a substring. {@link #findByVatNumber} stays
     * exact and is untouched by this — it is the authoritative auto-link of brief §5, and the whole
     * reason it may be applied without asking anybody is that it cannot match approximately.
     */
    @Override
    @Transactional(readOnly = true)
    public List<CustomerView> search(String term, boolean activeOnly) {
        return toViews(repository.findAll(
                Specifications.<Customer>activeOnly(activeOnly)
                        .and(TextSearch.matching(term, SEARCHABLE)),
                Sort.by(Sort.Order.asc("name"))));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerView> find(long id) {
        return repository.findById(id).map(CustomerServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerView require(long id) {
        return find(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerView require(CustomerSystemKey systemKey) {
        Objects.requireNonNull(systemKey, "systemKey");
        return repository.findBySystemKey(systemKey)
                .map(CustomerServiceImpl::toView)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "No customer carries the system key " + systemKey + ". It is seeded by "
                                + "migration V17, so its absence is a broken seed rather than a "
                                + "missing option — every retail sale needs somebody to be against."));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerView> findByVatNumber(String vatNumber) {
        String normalised = optionalText(vatNumber);
        if (normalised == null) {
            // Deliberately not "the first customer with no VAT number". This is the one lookup
            // whose result may be applied without confirmation, so it must never match on the
            // absence of the identifier that makes it safe.
            return Optional.empty();
        }
        return repository.findByVatNumberIgnoreCase(normalised).map(CustomerServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerView> suggestMatches(String nameFragment, String email, String phone) {
        String name = optionalText(nameFragment);
        String mail = optionalText(email);
        String tel = optionalText(phone);
        if (name == null && mail == null && tel == null) {
            // Nothing to go on returns nothing, rather than every customer presented as a
            // candidate — a suggestion list containing everyone is not a suggestion.
            return List.of();
        }

        // One query per supplied criterion, merged here. A criterion that was not supplied
        // contributes nothing, rather than matching everything.
        Map<Long, Customer> candidates = new LinkedHashMap<>();
        if (name != null) {
            index(candidates, repository.findByNameContainingIgnoreCaseOrderByNameAsc(name));
        }
        if (mail != null) {
            index(candidates, repository.findByEmailIgnoreCaseOrderByNameAsc(mail));
        }
        if (tel != null) {
            index(candidates, repository.findByPhoneOrderByNameAsc(tel));
        }

        return candidates.values().stream()
                .sorted(Comparator.comparing(Customer::getName, String.CASE_INSENSITIVE_ORDER))
                .map(CustomerServiceImpl::toView)
                .toList();
    }

    /** Keyed by id, so a customer matching on two criteria is offered once, not twice. */
    private static void index(Map<Long, Customer> candidates, List<Customer> found) {
        found.forEach(customer -> candidates.putIfAbsent(customer.getId(), customer));
    }

    @Override
    @Transactional
    public CustomerView create(NewCustomer request) {
        Objects.requireNonNull(request, "request");
        String name = requireText(request.name(), "Customer name");
        String vatNumber = optionalText(request.vatNumber());

        if (vatNumber != null && repository.existsByVatNumberIgnoreCase(vatNumber)) {
            throw new InvalidCustomerException(
                    "A customer with VAT number '" + vatNumber + "' already exists. The VAT "
                            + "number is the authoritative identifier for matching, so two "
                            + "customers cannot share one.");
        }
        requireCoherentVatStatus(name, request.vatStatus(), vatNumber,
                request.vatExemptionReasonId());
        requireActiveVatClassIfPresent(request.vatClassOverrideId());

        Customer saved = repository.save(new Customer(
                name,
                optionalText(request.email()),
                optionalText(request.phone()),
                vatNumber,
                request.vatStatus(),
                request.vatClassOverrideId(),
                request.vatExemptionReasonId()));

        auditLog.record("customer.created", ENTITY_TYPE, String.valueOf(saved.getId()), Map.of(
                "name", name,
                "vatStatus", request.vatStatus().name(),
                "vatNumber", vatNumber == null ? "(none)" : vatNumber));

        return toView(saved);
    }

    @Override
    @Transactional
    public CustomerView rename(long id, String newName) {
        String name = requireText(newName, "Customer name");
        Customer customer = load(id);

        String previous = customer.getName();
        customer.rename(name);

        auditLog.record("customer.renamed", ENTITY_TYPE, String.valueOf(id),
                Map.of("from", previous, "to", name));

        return toView(customer);
    }

    @Override
    @Transactional
    public CustomerView changeContactDetails(long id, String email, String phone) {
        Customer customer = load(id);
        customer.changeContactDetails(optionalText(email), optionalText(phone));

        auditLog.record("customer.contact-details-changed", ENTITY_TYPE, String.valueOf(id),
                Map.of("name", customer.getName()));

        return toView(customer);
    }

    @Override
    @Transactional
    public CustomerView changeVatNumber(long id, String vatNumber) {
        Customer customer = load(id);
        String normalised = optionalText(vatNumber);

        if (normalised != null
                && !normalised.equalsIgnoreCase(nullSafe(customer.getVatNumber()))
                && repository.existsByVatNumberIgnoreCase(normalised)) {
            throw new InvalidCustomerException(
                    "Another customer already has VAT number '" + normalised + "'.");
        }
        requireCoherentVatStatus(customer.getName(), customer.getVatStatus(), normalised,
                customer.getVatExemptionReasonId());

        customer.changeVatNumber(normalised);

        auditLog.record("customer.vat-number-changed", ENTITY_TYPE, String.valueOf(id), Map.of(
                "name", customer.getName(),
                "vatNumber", normalised == null ? "(none)" : normalised));

        return toView(customer);
    }

    @Override
    @Transactional
    public CustomerView changeVatStatus(long id, VatStatus vatStatus, Long vatExemptionReasonId) {
        Objects.requireNonNull(vatStatus, "vatStatus");
        Customer customer = load(id);

        requireCoherentVatStatus(customer.getName(), vatStatus, customer.getVatNumber(),
                vatExemptionReasonId);

        VatStatus previous = customer.getVatStatus();
        customer.changeVatStatus(vatStatus, vatExemptionReasonId);

        auditLog.record("customer.vat-status-changed", ENTITY_TYPE, String.valueOf(id), Map.of(
                "name", customer.getName(),
                "from", previous.name(),
                "to", vatStatus.name()));

        return toView(customer);
    }

    @Override
    @Transactional
    public CustomerView changeVatClassOverride(long id, Long vatClassId) {
        Customer customer = load(id);
        requireActiveVatClassIfPresent(vatClassId);

        customer.changeVatClassOverride(vatClassId);

        auditLog.record("customer.vat-class-override-changed", ENTITY_TYPE, String.valueOf(id),
                Map.of(
                        "name", customer.getName(),
                        "vatClassId", vatClassId == null ? "(cleared)" : String.valueOf(vatClassId)));

        return toView(customer);
    }

    @Override
    @Transactional
    public void deactivate(long id) {
        Customer customer = load(id);
        if (customer.getSystemKey() != null && !customer.getSystemKey().isDeactivatable()) {
            // Also a CHECK constraint, so it holds against a psql session too. Stated here as well
            // because a constraint name arriving at flush time explains nothing.
            throw new InvalidCustomerException(
                    "'" + customer.getName() + "' is a structural record (" + customer.getSystemKey()
                            + "), not a real customer, so it cannot be deactivated. Every till sale "
                            + "with no identified buyer is recorded against it, and deactivating it "
                            + "would leave those sales with nobody to be against.");
        }
        if (!customer.isActive()) {
            return;
        }
        customer.setActive(false);
        auditLog.record("customer.deactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("name", customer.getName()));
    }

    @Override
    @Transactional
    public void reactivate(long id) {
        Customer customer = load(id);
        if (customer.isActive()) {
            return;
        }
        customer.setActive(true);
        auditLog.record("customer.reactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("name", customer.getName()));
    }

    private Customer load(long id) {
        return repository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    /**
     * Also enforced by CHECK constraints. Duplicated here so that the failure explains itself
     * rather than arriving as a constraint name at flush time.
     */
    private void requireCoherentVatStatus(
            String name, VatStatus status, String vatNumber, Long exemptionReasonId) {
        if (status.requiresVatNumber() && vatNumber == null) {
            throw new InvalidCustomerException(
                    "Customer '" + name + "' cannot be " + status + " without a VAT number: with "
                            + "no counterparty VAT number the supply is a distance sale to a "
                            + "consumer, not a reverse-charged B2B supply.");
        }
        if (status.requiresExemptionReason() && exemptionReasonId == null) {
            throw new InvalidCustomerException(
                    "Customer '" + name + "' cannot be " + status + " without a VAT exemption "
                            + "reason naming the article it is exempt under.");
        }
        if (exemptionReasonId != null && exemptionReasons.find(exemptionReasonId).isEmpty()) {
            throw new InvalidCustomerException(
                    "No VAT exemption reason with id " + exemptionReasonId + ".");
        }
    }

    /**
     * An inactive class is refused as an override for the same reason it is refused as a charge
     * type's default: a deactivated class is one whose rate is no longer to be charged, and the
     * whole point of deactivating rather than editing it is that already-issued documents keep
     * their rate while new ones do not get it.
     */
    private void requireActiveVatClassIfPresent(Long vatClassId) {
        if (vatClassId == null) {
            return;
        }
        VatClassView vatClass = vatClasses.find(vatClassId).orElseThrow(() ->
                new InvalidCustomerException("No VAT class with id " + vatClassId + "."));
        if (!vatClass.active()) {
            throw new InvalidCustomerException(
                    "VAT class '" + vatClass.code() + "' is inactive, so it cannot be a "
                            + "customer's VAT override.");
        }
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new InvalidCustomerException(what + " must not be blank.");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static List<CustomerView> toViews(List<Customer> customers) {
        return customers.stream().map(CustomerServiceImpl::toView).toList();
    }

    private static CustomerView toView(Customer customer) {
        return new CustomerView(
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getVatNumber(),
                customer.getVatStatus(),
                customer.getVatClassOverrideId(),
                customer.getVatExemptionReasonId(),
                customer.getSystemKey(),
                customer.isActive());
    }
}
