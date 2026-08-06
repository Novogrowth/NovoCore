package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import java.util.Objects;

/**
 * A way a customer pays — <strong>the business's own row</strong>, referencing an AADE article.
 *
 * <h2>⚠️ R4 replaced a seed-only statutory list with a business list, and that is the whole record</h2>
 *
 * <p>V35 built one row per {@code SettlementMethod} enum value with no create path, on the reasoning
 * that adding a method needs code. <strong>The rows are the owner's to author</strong>: the list ships
 * empty, he creates every row, and each names both an AADE article and the account it reconciles to.
 * <em>Two POS terminals can share AADE code 7 and land in different bank accounts, and the article
 * cannot tell you which.</em>
 *
 * @param aadePaymentMethodId ⚠️ <strong>Mandatory.</strong> A payment method with no statutory article
 *     is not a real kind of thing, it is an incompletely specified row — unlike a <em>document type</em>
 *     with no AADE code, which is a real thing the business issues. The R1a analogy was examined and
 *     rejected; see {@code V37}.
 * @param accountId ⚠️ <strong>Mandatory</strong>, and it may be Accounts receivable. Επί πιστώσει
 *     <em>names</em> AR explicitly rather than carrying a null the posting code interprets by
 *     convention, so <strong>no null means anything</strong> and the posting rule reads the account the
 *     method names in every case.
 * @param settlesImmediately ⚠️ <strong>DERIVED from the account's KIND, never stored.</strong>
 *     {@code BANK_CASH} or {@code PARTNER_CLEARING} means the money is already somewhere and the
 *     invoice is born settled; the Accounts receivable control account means it is not, and the invoice
 *     is an open item until a Receipt allocates against it. It is a component here rather than an
 *     accessor only so that it reaches the wire — the fact is computed, not held.
 * @param subjectToCashLimit ⚠️ <strong>DERIVED from the article being annex 8.12 code 3
 *     (Μετρητά), never stored.</strong> The €500 rule is Greek law as NovoCore applies it, not an
 *     attribute AADE published, so it is not a column on the codification. ⚠️ <strong>It is the
 *     RETAIL threshold only</strong> — the B2B rule is €500 net plus VAT and needs a retail/B2B
 *     distinction the model does not have. Roadmap row <strong>C2</strong>; not R4's.
 * @param inUse whether a sales invoice names this method. ⚠️ The predicate that freezes every field —
 *     correctable while nothing has been recorded against it, refused afterwards. A flag rather than a
 *     sentence on purpose: the screen renders the reason, because the backend localises nothing
 *     (Q47(b)). ⚠️ A <strong>reversed</strong> invoice counts; recorded is not standing.
 * @param sortCode ⚠️ Ordering only, freely editable, on no document. See {@code V34}.
 */
public record PaymentMethodView(
        long id,
        @Mandatory String abbreviation,
        @Mandatory String description,
        long aadePaymentMethodId,
        int aadePaymentMethodCode,
        @Mandatory String aadePaymentMethodDescription,
        long accountId,
        @Mandatory String accountName,
        boolean settlesImmediately,
        boolean subjectToCashLimit,
        int sortCode,
        boolean inUse,
        boolean active) {

    public PaymentMethodView {
        Objects.requireNonNull(abbreviation, "abbreviation");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(aadePaymentMethodDescription, "aadePaymentMethodDescription");
        Objects.requireNonNull(accountName, "accountName");
    }
}
