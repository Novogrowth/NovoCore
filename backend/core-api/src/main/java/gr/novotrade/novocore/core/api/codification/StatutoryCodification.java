package gr.novotrade.novocore.core.api.codification;

import java.util.List;
import java.util.Optional;

/**
 * <strong>A code list an outside authority owns, which this system may use and may not author.</strong>
 *
 * <p>The whole content of this contract is the method that is <em>absent</em>. There is no
 * {@code create}, and there never will be, because a row here is not ours to add: if AADE defines a
 * new code, that is a migration with the artefact it was read from sitting beside it, not an API
 * call from a form. {@code StatutoryCodificationRulesTest} asserts the absence, so it is a build
 * failure rather than a convention somebody remembers.
 *
 * <h2>⚠️ Which lists are members, and which look like members and are not</h2>
 *
 * <p>The distinction is <strong>who authors a row</strong>, and getting it wrong in either direction
 * is expensive:
 *
 * <ul>
 *   <li><strong>Members:</strong> {@code AadeInvoiceTypeService} and
 *       {@code VatExemptionReasonService}. Both carry AADE codes that are transmitted, where a row
 *       somebody invented is a compliance defect rather than a data-entry mistake.
 *   <li><strong>Not a member, though it looks like one:</strong> {@code ChargeTypeService}. Its six
 *       write methods are unreachable today only because <em>its routes were never built</em>, not
 *       because it is seed-only. Adding "Gift wrapping" is a business decision. Freezing it under
 *       this contract because it resembles one from the outside is exactly the trap this
 *       distinction exists to avoid.
 *   <li><strong>Not members, and this is the correction R1a made:</strong> the sales and purchase
 *       <em>document type</em> lists. An earlier design had them here, with the AADE code as the
 *       row's identity. The owner's real Prosvasis Go configuration disproved it — six of his
 *       nineteen document types have <em>no AADE invoice type at all</em> (Προσφορά, Δελτίο
 *       Αποστολής, Παραγγελία and the rest are operational documents, not tax documents), and a
 *       model in which the code <em>is</em> the row cannot represent a document that has none.
 *       They are the business's own lists, with full CRUD and a nullable reference to
 *       {@link AadeInvoiceTypeView}.
 * </ul>
 *
 * <p><strong>The counter-argument was heard and is the reason this is an interface rather than a
 * copied shape:</strong> three copies of an unexercised decision is not a pattern. Deleting
 * {@code create} from one service without stating the contract would have left the next list to
 * rediscover the argument from scratch.
 *
 * <h2>What a user may do</h2>
 *
 * <p>Deactivate a code that no longer applies, reactivate it, and correct its description. Nothing
 * else. {@code deactivate}/{@code reactivate} rather than {@code activate} is not arbitrary: it is
 * the vocabulary {@code VatClass}, {@code UnitOfMeasure} and {@code ChargeType} already use, and a
 * codification that spelled the same operation differently would be one more thing to look up.
 *
 * <p>⚠️ {@code active} on a statutory codification means <strong>"the authority still publishes
 * this code"</strong> — never "this business uses it". The second question belongs to the business
 * lists, and conflating the two is what made the previous document-type model unbuildable: it asked
 * a seed to know which types the business issues, a fact that appears nowhere in AADE's artefacts.
 *
 * @param <V> the view this codification returns
 */
public interface StatutoryCodification<V> {

    /** Every code, active and inactive, in the authority's own code order. */
    List<V> all();

    /** Active codes only — what a picker should offer. */
    List<V> active();

    Optional<V> find(long id);

    /** @throws RuntimeException a {@code ...NotFoundException} if the id names nothing */
    V require(long id);

    /**
     * Corrects the description.
     *
     * <p>The one field a user may edit, because a description is a label rather than a statutory
     * fact: correcting a typo changes nothing about what a document declares. The code is the
     * identity and is not editable in any implementation.
     */
    V describe(long id, String description);

    /** Takes a code out of circulation without deleting it. Nothing is ever deleted. */
    void deactivate(long id);

    void reactivate(long id);
}
