package gr.novotrade.novocore.core.web;

import gr.novotrade.novocore.core.api.account.AccountGroupNotFoundException;
import gr.novotrade.novocore.core.api.account.AccountNotFoundException;
import gr.novotrade.novocore.core.api.account.InvalidAccountException;
import gr.novotrade.novocore.core.api.asset.AssetNotFoundException;
import gr.novotrade.novocore.core.api.asset.InvalidAssetException;
import gr.novotrade.novocore.core.api.attachment.AttachmentTooLargeException;
import gr.novotrade.novocore.core.api.backup.BackupNotConfiguredException;
import gr.novotrade.novocore.core.api.banking.BankTransferNotFoundException;
import gr.novotrade.novocore.core.api.banking.InvalidBankTransferException;
import gr.novotrade.novocore.core.api.bundle.BundleNotDecomposableException;
import gr.novotrade.novocore.core.api.bundle.InvalidBundleException;
import gr.novotrade.novocore.core.api.charge.ChargeTypeNotFoundException;
import gr.novotrade.novocore.core.api.charge.InvalidChargeTypeException;
import gr.novotrade.novocore.core.api.customer.CustomerNotFoundException;
import gr.novotrade.novocore.core.api.customer.InvalidCustomerException;
import gr.novotrade.novocore.core.api.email.EmailAttachmentUnavailableException;
import gr.novotrade.novocore.core.api.email.EmailNotConfiguredException;
import gr.novotrade.novocore.core.api.inventory.InvalidInventoryLotException;
import gr.novotrade.novocore.core.api.inventory.InvalidStockConsumptionException;
import gr.novotrade.novocore.core.api.inventory.InvalidStockWriteOffException;
import gr.novotrade.novocore.core.api.inventory.InventoryLotNotFoundException;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitNotFoundException;
import gr.novotrade.novocore.core.api.inventory.StockConsumptionNotFoundException;
import gr.novotrade.novocore.core.api.inventory.StockNotApplicableException;
import gr.novotrade.novocore.core.api.inventory.StockWriteOffNotFoundException;
import gr.novotrade.novocore.core.api.ledger.InvalidJournalEntryException;
import gr.novotrade.novocore.core.api.ledger.JournalEntryNotAmendableException;
import gr.novotrade.novocore.core.api.ledger.JournalEntryNotFoundException;
import gr.novotrade.novocore.core.api.ledger.UnbalancedJournalEntryException;
import gr.novotrade.novocore.core.api.product.InvalidProductException;
import gr.novotrade.novocore.core.api.product.InvalidUnitOfMeasureException;
import gr.novotrade.novocore.core.api.product.ProductNotFoundException;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureNotFoundException;
import gr.novotrade.novocore.core.api.purchasing.FreightAllocationNotFoundException;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptNotFoundException;
import gr.novotrade.novocore.core.api.purchasing.InvalidFreightAllocationException;
import gr.novotrade.novocore.core.api.purchasing.InvalidGoodsReceiptException;
import gr.novotrade.novocore.core.api.purchasing.InvalidPurchaseInvoiceException;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceNotFoundException;
import gr.novotrade.novocore.core.api.sales.CreditNoteNotFoundException;
import gr.novotrade.novocore.core.api.sales.InvalidCreditNoteException;
import gr.novotrade.novocore.core.api.sales.InvalidSalesInvoiceException;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceNotFoundException;
import gr.novotrade.novocore.core.api.security.InvalidRoleException;
import gr.novotrade.novocore.core.api.security.InvalidUserException;
import gr.novotrade.novocore.core.api.security.NotAuthenticatedException;
import gr.novotrade.novocore.core.api.security.RoleNotFoundException;
import gr.novotrade.novocore.core.api.security.SectionAccessDeniedException;
import gr.novotrade.novocore.core.api.security.UserNotFoundException;
import gr.novotrade.novocore.core.api.settings.SettingNotFoundException;
import gr.novotrade.novocore.core.api.settings.SettingValueException;
import gr.novotrade.novocore.core.api.settlement.InvalidSettlementException;
import gr.novotrade.novocore.core.api.settlement.SettlementNotFoundException;
import gr.novotrade.novocore.core.api.supplier.InvalidSupplierException;
import gr.novotrade.novocore.core.api.supplier.SupplierNotFoundException;
import gr.novotrade.novocore.core.api.tax.InvalidVatClassException;
import gr.novotrade.novocore.core.api.tax.InvalidVatExemptionReasonException;
import gr.novotrade.novocore.core.api.tax.VatClassNotDeterminableException;
import gr.novotrade.novocore.core.api.tax.VatClassNotFoundException;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns the core's typed exceptions into HTTP status codes.
 *
 * <p>This lives here rather than as {@code @ResponseStatus} annotations on the exceptions
 * themselves, because those exceptions are in {@code core-api}, which is not permitted a Spring
 * dependency — an architecture test enforces it. Mapping a domain failure to a transport concern is
 * the web layer's job anyway.
 *
 * <h2>The deliberate asymmetry: which refusals explain themselves</h2>
 *
 * <p><strong>Permission refusals say nothing.</strong> Their messages name the role, the section and
 * what was required, which is useful to an administrator reading the log and is exactly what should
 * not be returned to a caller who has just been refused: it confirms the section exists and
 * describes the permission model.
 *
 * <p><strong>Validation refusals say everything.</strong> The core's {@code Invalid…} messages are
 * written to be read — "a supplier SKU without a supplier", "cash settlements are limited to €500" —
 * and an operator who cannot see why a document was refused cannot fix it. Withholding them would
 * make every 422 a support call. These are facts about data the caller just sent, so returning them
 * discloses nothing the caller did not already have.
 *
 * <p><strong>Programming errors say nothing either.</strong> A bare {@link IllegalArgumentException}
 * is not an operator's mistake, so its message is logged and the caller gets a bare 400.
 *
 * <h2>The list is explicit, and a test keeps it complete</h2>
 *
 * <p>Every exception is named rather than matched on a superclass or a naming convention, because
 * every one of them extends {@code RuntimeException} directly and there is no hierarchy to match on.
 * The cost of an explicit list is that a new exception added to {@code core-api} would fall through
 * to a 500 unnoticed, so {@code WebExceptionMappingTest} enumerates the exceptions in
 * {@code core-api} and fails the build if one is not handled here.
 */
/*
 * Ordered ahead of Boot's own ProblemDetailsExceptionHandler, which spring.mvc.problemdetails
 * registers as a second advice over the same framework exceptions. Without this, Spring answers
 * HttpMessageNotReadableException itself with a generic "Failed to read request", and step 14's most
 * load-bearing message — the one telling a client that an amount must be a JSON string and not a
 * number — is replaced by nothing useful. Step 15 caught it the moment problemdetails was turned on:
 * the test asserting that the money rule reaches the caller went red immediately.
 *
 * Both advices are wanted, in this order: this one for the exceptions it names, Boot's for the rest
 * of what Spring raises. Every error stays an RFC 7807 body either way, which is the point of having
 * turned the property on.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    // -------------------------------------------------------------------------------------------
    // 404 — the thing named does not exist
    // -------------------------------------------------------------------------------------------

    /**
     * The body is generic on purpose. "Which id was wrong" is already in the caller's own request,
     * and echoing the core's message would confirm the existence of neighbouring records for a
     * caller probing ids.
     */
    @ExceptionHandler({
        AccountGroupNotFoundException.class,
        AccountNotFoundException.class,
        AssetNotFoundException.class,
        BankTransferNotFoundException.class,
        ChargeTypeNotFoundException.class,
        CreditNoteNotFoundException.class,
        CustomerNotFoundException.class,
        FreightAllocationNotFoundException.class,
        GoodsReceiptNotFoundException.class,
        InventoryLotNotFoundException.class,
        JournalEntryNotFoundException.class,
        ProductNotFoundException.class,
        PurchaseInvoiceNotFoundException.class,
        RoleNotFoundException.class,
        SalesInvoiceNotFoundException.class,
        SerializedUnitNotFoundException.class,
        SettingNotFoundException.class,
        SettlementNotFoundException.class,
        StockConsumptionNotFoundException.class,
        StockWriteOffNotFoundException.class,
        SupplierNotFoundException.class,
        UnitOfMeasureNotFoundException.class,
        UserNotFoundException.class,
        VatClassNotFoundException.class,
        VatExemptionReasonNotFoundException.class,
    })
    ProblemDetail notFound(RuntimeException exception) {
        log.debug("Not found: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Not found.");
    }

    // -------------------------------------------------------------------------------------------
    // 422 — well-formed, but the domain refuses it
    // -------------------------------------------------------------------------------------------

    /**
     * 422 rather than 400: the request was understood and syntactically fine, and a business rule
     * refused it. A client cannot fix a 422 by correcting its JSON, which is what 400 would imply.
     */
    @ExceptionHandler({
        BundleNotDecomposableException.class,
        InvalidAccountException.class,
        InvalidAssetException.class,
        InvalidBankTransferException.class,
        InvalidBundleException.class,
        InvalidChargeTypeException.class,
        InvalidCreditNoteException.class,
        InvalidCustomerException.class,
        InvalidFreightAllocationException.class,
        InvalidGoodsReceiptException.class,
        InvalidInventoryLotException.class,
        InvalidJournalEntryException.class,
        InvalidProductException.class,
        InvalidPurchaseInvoiceException.class,
        InvalidRoleException.class,
        InvalidSalesInvoiceException.class,
        InvalidSettlementException.class,
        InvalidStockConsumptionException.class,
        InvalidStockWriteOffException.class,
        InvalidSupplierException.class,
        InvalidUnitOfMeasureException.class,
        InvalidUserException.class,
        InvalidVatClassException.class,
        InvalidVatExemptionReasonException.class,
        SettingValueException.class,
        StockNotApplicableException.class,
        UnbalancedJournalEntryException.class,
        VatClassNotDeterminableException.class,
    })
    ProblemDetail refusedByTheDomain(RuntimeException exception) {
        log.info("Refused: {}", exception.getMessage());
        // UNPROCESSABLE_CONTENT, not the deprecated UNPROCESSABLE_ENTITY: RFC 9110 renamed 422 and
        // Spring 7 follows it. Same status code, current name.
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, message(exception));
    }

    // -------------------------------------------------------------------------------------------
    // 409 — a conflict with the record's current state
    // -------------------------------------------------------------------------------------------

    /**
     * Distinct from 422 because nothing about the request is wrong: the document is immutable, or
     * has already been reversed (ADR 0006). Resending it corrected would not help; the caller has
     * to do something else, which is what 409 says and 422 does not.
     */
    @ExceptionHandler(JournalEntryNotAmendableException.class)
    ProblemDetail conflict(JournalEntryNotAmendableException exception) {
        log.info("Conflict: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message(exception));
    }

    // -------------------------------------------------------------------------------------------
    // 410 — it existed, and its bytes are gone
    // -------------------------------------------------------------------------------------------

    /**
     * The referenced document was deleted, or an inline copy was pruned (ADR 0012). 410 rather than
     * 404 keeps the distinction the core is careful to make: the attachment record still exists and
     * still names the file — it is the content that is gone, with a reason worth returning.
     */
    @ExceptionHandler(EmailAttachmentUnavailableException.class)
    ProblemDetail gone(EmailAttachmentUnavailableException exception) {
        log.info("Gone: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, message(exception));
    }

    // -------------------------------------------------------------------------------------------
    // 413 — too big
    // -------------------------------------------------------------------------------------------

    @ExceptionHandler(AttachmentTooLargeException.class)
    ProblemDetail tooLarge(AttachmentTooLargeException exception) {
        log.info("Rejected as too large: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, message(exception));
    }

    // -------------------------------------------------------------------------------------------
    // 503 — the feature exists but has never been configured
    // -------------------------------------------------------------------------------------------

    /**
     * Not the caller's fault and not a permanent failure — an operator has to fill in a setting.
     * The message says which, and it is returned because only an authorised caller can reach this
     * and the alternative is a 500 that names nothing.
     */
    @ExceptionHandler({
        BackupNotConfiguredException.class,
        EmailNotConfiguredException.class,
    })
    ProblemDetail notConfigured(RuntimeException exception) {
        log.warn("Not configured: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, message(exception));
    }

    // -------------------------------------------------------------------------------------------
    // 403 / 401 — permission. Deliberately generic; see the class javadoc.
    // -------------------------------------------------------------------------------------------

    /**
     * 403 — authenticated, but this role may not see the section.
     *
     * <p>Distinct from the 401 the filter chain returns for an unauthenticated call: logging in
     * again would not help here.
     */
    @ExceptionHandler(SectionAccessDeniedException.class)
    ProblemDetail sectionAccessDenied(SectionAccessDeniedException exception) {
        log.warn("Refused: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied.");
    }

    /**
     * 403 — a handler carries no {@link Requires} declaration, so nothing could be checked.
     *
     * <p>A defect in our code rather than the caller's, and still a refusal: the request must not
     * succeed, and the caller must not be told that an endpoint exists but is misconfigured. It is
     * indistinguishable from an ordinary access denial from the outside, which is intended.
     */
    @ExceptionHandler(UndeclaredEndpointException.class)
    ProblemDetail undeclaredEndpoint(UndeclaredEndpointException exception) {
        log.error("Refusing an undeclared endpoint: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied.");
    }

    /**
     * 401 — no authenticated user.
     *
     * <p>Normally unreachable, since the filter chain rejects unauthenticated requests to
     * {@code /api/**} first. It exists so that a future endpoint accidentally left out of the
     * authenticated matcher fails closed here rather than throwing a 500 and leaking a stack
     * trace.
     */
    @ExceptionHandler(NotAuthenticatedException.class)
    ProblemDetail notAuthenticated(NotAuthenticatedException exception) {
        log.warn("Unauthenticated request reached a controller: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication required.");
    }

    // -------------------------------------------------------------------------------------------
    // 400 — the request itself is wrong
    // -------------------------------------------------------------------------------------------

    /**
     * A body that could not be parsed into what the handler expects.
     *
     * <p>The reason <em>is</em> returned, unlike other 400s, because the commonest cause here is a
     * client sending a JSON number where an amount or a quantity must be a string. That rule is not
     * guessable from a bare 400, and the message names it. What is echoed describes the shape of
     * the body the caller just sent, so it discloses nothing the caller did not already have.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadableBody(HttpMessageNotReadableException exception) {
        log.info("Unreadable request body: {}", exception.getMessage());
        Throwable cause = exception.getMostSpecificCause();
        String detail = cause.getMessage() == null
                ? "Malformed request body."
                : "Malformed request body: " + firstLine(cause.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    /**
     * 400, <strong>carrying its message</strong>. The caller named a combination of query
     * parameters the route cannot answer, and the message says which combinations it can.
     *
     * <p>The counterpart to the generic 400 below, and the distinction is the whole point of the
     * two existing: this describes the <em>request</em>, which the caller already has, so echoing it
     * discloses nothing while making the route usable. See {@link InvalidRequestException} for how
     * seventeen such messages came to be discarded.
     */
    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalidRequest(InvalidRequestException exception) {
        log.info("Invalid request: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message(exception));
    }

    /**
     * 400, generic. An {@link IllegalArgumentException} out of the core is a mistake in calling
     * code — a null where one is not allowed, an id naming nothing on a path that treats that as a
     * bug — not something an operator did. Its message is for the log.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException exception) {
        log.warn("Bad request: {}", exception.getMessage(), exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Bad request.");
    }

    // -------------------------------------------------------------------------------------------

    /** Never null, so a message-less exception cannot produce a body saying "null". */
    private static String message(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Refused." : message;
    }

    /**
     * Jackson's messages carry a location and a reference chain on later lines; the first line is
     * the reason. Keeping only that avoids returning a stack-shaped string to a browser.
     */
    private static String firstLine(String message) {
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
