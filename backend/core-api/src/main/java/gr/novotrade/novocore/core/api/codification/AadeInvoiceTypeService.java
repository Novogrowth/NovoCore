package gr.novotrade.novocore.core.api.codification;

import java.util.List;
import java.util.Optional;

/**
 * The AADE myDATA invoice-type codification — all 55 values of the {@code InvoiceType} enumeration.
 *
 * <p><strong>Seeded by Flyway from the artefacts in {@code docs/aade/v2.0.1/} and by nothing
 * else.</strong> Codes come from {@code SimpleTypes-v2.0.1.xsd}, where they are flat
 * {@code <xs:enumeration>} elements with no layout to lose; Greek descriptions and the group
 * headings come from a rasterised reading of annex 8.1. Never from a text dump of the annex — that
 * extractor drifts the code and description columns apart on these tables, producing clean,
 * plausible, off-by-one pairs.
 *
 * <p>It implements {@link StatutoryCodification}, so there is deliberately no {@code create}. See
 * that interface for which lists are members and which merely resemble one.
 */
public interface AadeInvoiceTypeService extends StatutoryCodification<AadeInvoiceTypeView> {

    /** @throws AadeInvoiceTypeNotFoundException if absent */
    @Override
    AadeInvoiceTypeView require(long id);

    /** By AADE code — {@code "1.1"}, {@code "11.1"}. Case-insensitive; codes are numeric anyway. */
    Optional<AadeInvoiceTypeView> findByCode(String code);

    /** @throws AadeInvoiceTypeNotFoundException if absent */
    AadeInvoiceTypeView requireByCode(String code);

    /**
     * The codes of one annex 8.1 group.
     *
     * <p>This is the query a document-type form makes: offering all 55 when the operator is
     * defining a <em>sales</em> document type would put "Ενοίκιο Έξοδο" in the picker.
     */
    List<AadeInvoiceTypeView> inGroup(AadeInvoiceGroup group);

    /**
     * The codes Novocore treats as issued by us, i.e. the two issuer groups — 34 of the 55.
     *
     * <p>⚠️ The {@link AadeInvoiceGroup#ENTITY_ADJUSTING} codes are in neither this nor
     * {@link #received()}. They are the entity's own journal entries and belong to no document
     * list; that is a fact about the codification rather than an omission here.
     */
    List<AadeInvoiceTypeView> issued();

    /** The codes Novocore treats as received — the two recipient groups, 15 of the 55. */
    List<AadeInvoiceTypeView> received();
}
