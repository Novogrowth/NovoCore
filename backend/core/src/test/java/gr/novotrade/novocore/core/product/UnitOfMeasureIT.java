package gr.novotrade.novocore.core.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.product.InvalidProductException;
import gr.novotrade.novocore.core.api.product.InvalidUnitOfMeasureException;
import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.NewUnitOfMeasure;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureNotFoundException;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureView;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Units of measure as a runtime-editable table (Q34), replacing the step 5 enum.
 *
 * <p>The conversion is the interesting part: a unit has to be addable without a deployment, and it
 * has to be able to carry a myDATA code — which is AADE's data, and therefore not something a Java
 * enum constant can own.
 */
class UnitOfMeasureIT extends AbstractCoreIntegrationTest {

    /** The eight units V11 seeds, carried over from the step 5 enum. */
    private static final List<String> SEEDED_CODES = List.of(
            "GRAM", "KILOGRAM", "LITRE", "METRE", "MILLILITRE", "PACK", "PIECE", "SET");

    @Autowired
    private UnitOfMeasureService units;

    @Autowired
    private ProductService products;

    @Autowired
    private VatClassService vatClasses;

    @Autowired
    private JdbcTemplate jdbc;

    private long standardRateId() {
        return vatClasses.requireByCode("1410").id();
    }

    /**
     * The units V11 seeded, excluding anything a test created.
     *
     * <p>Scoped for the reason {@code ChartOfAccountsIT} scopes its counts: these tests share one
     * non-transactional database, so an assertion about "the seeded units" that counted every row
     * would be a guarantee about JUnit's ordering rather than about the seed. Test fixtures here are
     * prefixed {@code UOMIT-} so they can be excluded by name.
     */
    private List<UnitOfMeasureView> seeded() {
        return units.all().stream()
                .filter(unit -> !unit.code().startsWith("UOMIT-"))
                .toList();
    }

    @Test
    @DisplayName("the eight units the enum carried are seeded, with the same fractional answers")
    void seededUnits() {
        assertThat(seeded()).extracting(UnitOfMeasureView::code)
                .containsExactlyElementsOf(SEEDED_CODES);
        assertThat(seeded()).allSatisfy(unit -> assertThat(unit.active()).isTrue());

        // The behaviour that used to live on the enum and is now data on the row. Three of
        // something sold by the piece is three; 2.5 pieces is a data-entry error.
        assertThat(units.requireByCode("PIECE").allowsFractionalQuantity()).isFalse();
        assertThat(units.requireByCode("SET").allowsFractionalQuantity()).isFalse();
        assertThat(units.requireByCode("PACK").allowsFractionalQuantity()).isFalse();
        // Coffee sells by weight, which is why Quantity carries six decimals at all.
        assertThat(units.requireByCode("KILOGRAM").allowsFractionalQuantity()).isTrue();
        assertThat(units.requireByCode("GRAM").allowsFractionalQuantity()).isTrue();
    }

    @Test
    @DisplayName("no myDATA unit code was invented — every seeded unit has none")
    void noGuessedMydataCodes() {
        // Same stance as the OSS/IOSS exemption reasons in V8. The verified AADE unit list has not
        // been supplied, and a plausible wrong code is invisible where an absent one is not.
        assertThat(units.withoutMydataCode()).extracting(UnitOfMeasureView::code)
                .containsAll(SEEDED_CODES);
        assertThat(seeded()).allSatisfy(unit ->
                assertThat(unit.mydataCodeIfAny()).isEmpty());

        UnitOfMeasureView piece = units.requireByCode("PIECE");
        assertThat(piece.mydataCodeIfAny()).isEmpty();

        // Phase 7's obligation, enforced here rather than left as a comment: transmission must fail
        // naming the unit, not send a blank or a code composed on the spot.
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(piece::requireMydataCode)
                .withMessageContaining("PIECE")
                .withMessageContaining("cannot be transmitted");
    }

    @Test
    @DisplayName("a unit can be added at runtime, which is the whole point of it being a table")
    void unitsAreAddableWithoutADeployment() {
        UnitOfMeasureView bag = units.create(NewUnitOfMeasure.withoutMydataCode(
                "UOMIT-BAG", "UomIT bag of 60kg", false));

        assertThat(bag.code()).isEqualTo("UOMIT-BAG");
        assertThat(bag.active()).isTrue();
        assertThat(units.requireByCode("uomit-bag").id())
                .as("code lookup is case-insensitive")
                .isEqualTo(bag.id());

        // A judgement about how the business sells rather than a physical fact, so it is editable.
        assertThat(units.changeFractionalQuantityAllowed(bag.id(), true)
                .allowsFractionalQuantity()).isTrue();
        assertThat(units.rename(bag.id(), "UomIT bag").name()).isEqualTo("UomIT bag");
    }

    @Test
    @DisplayName("a myDATA code is recorded once and is not changeable afterwards")
    void mydataCodeIsWriteOnce() {
        UnitOfMeasureView unit = units.create(NewUnitOfMeasure.withoutMydataCode(
                "UOMIT-ONCE", "UomIT write-once", false));

        UnitOfMeasureView mapped = units.recordMydataCode(unit.id(), "UOMIT-AADE-1");
        assertThat(mapped.requireMydataCode()).isEqualTo("UOMIT-AADE-1");
        assertThat(units.withoutMydataCode()).extracting(UnitOfMeasureView::code)
                .doesNotContain("UOMIT-ONCE");

        // A myDATA code that has been transmitted describes documents already filed under it, so
        // changing it would silently re-describe them. Deactivate and replace instead.
        assertThatExceptionOfType(InvalidUnitOfMeasureException.class)
                .isThrownBy(() -> units.recordMydataCode(unit.id(), "UOMIT-AADE-2"))
                .withMessageContaining("not changeable")
                .withMessageContaining("already have been transmitted");

        // And two units cannot share one, since it is what goes on the wire.
        UnitOfMeasureView other = units.create(NewUnitOfMeasure.withoutMydataCode(
                "UOMIT-OTHER", "UomIT other", false));
        assertThatExceptionOfType(InvalidUnitOfMeasureException.class)
                .isThrownBy(() -> units.recordMydataCode(other.id(), "UOMIT-AADE-1"))
                .withMessageContaining("already has myDATA code");
    }

    @Test
    @DisplayName("duplicate codes and names are refused")
    void duplicatesAreRefused() {
        units.create(NewUnitOfMeasure.withoutMydataCode("UOMIT-DUP", "UomIT duplicate", false));

        assertThatExceptionOfType(InvalidUnitOfMeasureException.class)
                .isThrownBy(() -> units.create(NewUnitOfMeasure.withoutMydataCode(
                        "uomit-dup", "UomIT different name", false)))
                .withMessageContaining("code 'uomit-dup' already exists");

        assertThatExceptionOfType(InvalidUnitOfMeasureException.class)
                .isThrownBy(() -> units.create(NewUnitOfMeasure.withoutMydataCode(
                        "UOMIT-DUP-2", "uomit duplicate", false)))
                .withMessageContaining("already exists");
    }

    // ---------------------------------------------------------------------------------------
    // How a product uses one
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("a product carries its unit as a reference, and can be moved between units")
    void productReferencesAUnit() {
        long kilogram = units.requireByCode("KILOGRAM").id();
        ProductView product = products.create(NewProduct.goods(
                "UomIT-COFFEE-01", "UomIT house blend", kilogram, standardRateId(),
                Money.ofEur("18.00")));

        assertThat(product.unitOfMeasure().code()).isEqualTo("KILOGRAM");
        assertThat(product.unitOfMeasure().allowsFractionalQuantity()).isTrue();

        ProductView moved = products.changeUnitOfMeasure(
                product.id(), units.requireByCode("GRAM").id());
        assertThat(moved.unitOfMeasure().code()).isEqualTo("GRAM");

        assertThatExceptionOfType(InvalidProductException.class)
                .isThrownBy(() -> products.changeUnitOfMeasure(product.id(), 999_999L))
                .withMessageContaining("No unit of measure with id 999999");
    }

    @Test
    @DisplayName("an inactive unit cannot be given to a product")
    void inactiveUnitIsRefused() {
        UnitOfMeasureView retired = units.create(NewUnitOfMeasure.withoutMydataCode(
                "UOMIT-RETIRED", "UomIT retired", false));
        units.deactivate(retired.id());

        // Deactivated precisely so nothing new is expressed in it — the same reasoning as an
        // inactive VAT class.
        assertThatExceptionOfType(InvalidProductException.class)
                .isThrownBy(() -> products.create(NewProduct.goods(
                        "UomIT-BADUNIT-01", "UomIT inactive unit", retired.id(),
                        standardRateId(), null)))
                .withMessageContaining("inactive");
    }

    @Test
    @DisplayName("a unit still used by a product cannot be deactivated")
    void unitInUseCannotBeDeactivated() {
        UnitOfMeasureView unit = units.create(NewUnitOfMeasure.withoutMydataCode(
                "UOMIT-INUSE", "UomIT in use", false));
        products.create(NewProduct.goods(
                "UomIT-INUSE-01", "UomIT product holding a unit", unit.id(), standardRateId(),
                null));

        // Refused rather than cascaded: a product whose unit has been retired carries a quantity
        // that no longer states what it counts, and step 6's lots inherit that quantity.
        assertThatExceptionOfType(InvalidUnitOfMeasureException.class)
                .isThrownBy(() -> units.deactivate(unit.id()))
                .withMessageContaining("still used by 1 product")
                .withMessageContaining("stating nothing");

        assertThat(units.require(unit.id()).active()).isTrue();
    }

    @Test
    @DisplayName("a missing unit names what it was asked for")
    void missingUnit() {
        assertThatExceptionOfType(UnitOfMeasureNotFoundException.class)
                .isThrownBy(() -> units.require(999_999L))
                .withMessageContaining("999999");

        assertThatExceptionOfType(UnitOfMeasureNotFoundException.class)
                .isThrownBy(() -> units.requireByCode("UOMIT-NOT-A-UNIT"))
                .withMessageContaining("UOMIT-NOT-A-UNIT");

        assertThat(units.findByCode(null)).isEmpty();
    }

    // ---------------------------------------------------------------------------------------
    // Enforced by the database
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("the old enum column is gone and the reference replaced it")
    void theEnumColumnIsGone() {
        List<String> columns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'product'
                  AND column_name LIKE '%unit%'
                """, String.class);

        assertThat(columns)
                .as("V11 converted the varchar enum column into a foreign key")
                .containsExactly("unit_of_measure_id")
                .doesNotContain("unit_of_measure");

        // NOT NULL: a product's unit is not guessable, so there is no default to fall back on.
        assertThat(jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'product'
                  AND column_name = 'unit_of_measure_id'
                """, String.class))
                .isEqualTo("NO");
    }

    @Test
    @DisplayName("the database refuses a product pointing at a unit that does not exist")
    void databaseEnforcesTheForeignKey() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO product (sku, name, product_type, unit_of_measure_id,
                                     default_vat_class_id)
                VALUES ('UomIT-PROBE-FK', 'Probe: unknown unit', 'GOODS', 999999, ?)
                """, standardRateId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("product_unit_of_measure_fk");
    }

    @Test
    @DisplayName("a blank myDATA code is refused, so \"none\" has one representation")
    void databaseRefusesBlankMydataCode() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO unit_of_measure (code, name, fractional_quantity_allowed, mydata_code)
                VALUES ('UOMIT-PROBE-BLANK', 'Probe: blank myDATA code', false, '  ')
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("unit_of_measure_mydata_code_not_blank");
    }
}
