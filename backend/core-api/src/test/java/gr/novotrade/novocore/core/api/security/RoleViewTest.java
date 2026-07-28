package gr.novotrade.novocore.core.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The permission model, tested as pure logic.
 *
 * <p>This is where the two-layer model from brief §7 is actually verified. Because the decision
 * lives on {@link RoleView} rather than in a service, none of this needs a database, a Spring
 * context or a logged-in user — so it can afford to be exhaustive, and it runs in the fast suite.
 */
class RoleViewTest {

    /** Owner and Admin as migration V6 seeds them. */
    private static RoleView fullAccessRole() {
        return new RoleView(1L, "OWNER", "Everything", true, true, true, Map.of(), Set.of());
    }

    /** Remote/Order Staff exactly as migration V6 seeds it — the concrete Q21 case. */
    private static RoleView remoteOrderStaff() {
        return new RoleView(
                2L,
                "REMOTE_ORDER_STAFF",
                "Home-based order staff",
                false,
                false,
                true,
                Map.of(
                        Section.SALES_ORDER_FULFILLMENT, AccessLevel.FULL,
                        Section.CUSTOMERS, AccessLevel.FULL,
                        Section.BACK_IN_STOCK_REMINDERS, AccessLevel.FULL,
                        Section.PRODUCTS, AccessLevel.VIEW),
                Set.of(
                        ProtectedField.PRODUCT_LAST_PURCHASE_PRICE,
                        ProtectedField.PRODUCT_SUPPLIER,
                        ProtectedField.PRODUCT_SUPPLIER_SKU));
    }

    @Nested
    @DisplayName("Remote/Order Staff — the concrete case this was built around")
    class RemoteOrderStaff {

        @Test
        @DisplayName("has full access to fulfillment, customers and back-in-stock")
        void fullAccessSections() {
            RoleView role = remoteOrderStaff();

            for (Section section : EnumSet.of(
                    Section.SALES_ORDER_FULFILLMENT,
                    Section.CUSTOMERS,
                    Section.BACK_IN_STOCK_REMINDERS)) {
                assertThat(role.accessTo(section))
                        .as("access to %s", section)
                        .isEqualTo(AccessLevel.FULL);
                assertThat(role.canView(section)).isTrue();
                assertThat(role.canEdit(section)).isTrue();
            }
        }

        @Test
        @DisplayName("can view Products but not change them")
        void productsAreViewOnly() {
            RoleView role = remoteOrderStaff();

            assertThat(role.accessTo(Section.PRODUCTS)).isEqualTo(AccessLevel.VIEW);
            assertThat(role.canView(Section.PRODUCTS)).isTrue();
            assertThat(role.canEdit(Section.PRODUCTS)).isFalse();
            assertThatExceptionOfType(SectionAccessDeniedException.class)
                    .isThrownBy(() -> role.requireEdit(Section.PRODUCTS));
        }

        @Test
        @DisplayName("cannot see any product cost or supplier field")
        void costAndSupplierFieldsAreHidden() {
            RoleView role = remoteOrderStaff();

            // The whole of Q21's field-level answer. An order picker needs to know what a product
            // is and what it sells for; not what it cost us or who supplies it.
            assertThat(role.canSee(ProtectedField.PRODUCT_LAST_PURCHASE_PRICE)).isFalse();
            assertThat(role.canSee(ProtectedField.PRODUCT_SUPPLIER)).isFalse();
            assertThat(role.canSee(ProtectedField.PRODUCT_SUPPLIER_SKU)).isFalse();

            assertThat(role.hiddenFieldsIn(Section.PRODUCTS))
                    .containsExactlyInAnyOrder(
                            ProtectedField.PRODUCT_LAST_PURCHASE_PRICE,
                            ProtectedField.PRODUCT_SUPPLIER,
                            ProtectedField.PRODUCT_SUPPLIER_SKU);
        }

        @Test
        @DisplayName("everything else in the system is invisible")
        void everythingElseIsInvisible() {
            RoleView role = remoteOrderStaff();

            Set<Section> granted = EnumSet.of(
                    Section.SALES_ORDER_FULFILLMENT,
                    Section.CUSTOMERS,
                    Section.BACK_IN_STOCK_REMINDERS,
                    Section.PRODUCTS);

            // Asserted over every section that exists rather than a hand-listed few, so a section
            // added later is covered by this test the day it appears — which is the same property
            // default-deny gives the production code.
            for (Section section : EnumSet.complementOf(EnumSet.copyOf(granted))) {
                assertThat(role.accessTo(section))
                        .as("%s must be invisible to Remote/Order Staff", section)
                        .isEqualTo(AccessLevel.NONE);
                assertThat(role.canView(section)).isFalse();
                assertThatExceptionOfType(SectionAccessDeniedException.class)
                        .isThrownBy(() -> role.requireView(section));
            }

            assertThat(role.visibleSections()).isEqualTo(granted);
        }

        @Test
        @DisplayName("specifically cannot reach the chart of accounts, settings or the audit log")
        void cannotReachFinancialSections() {
            // Named explicitly as well as covered by the sweep above, because these are the ones
            // whose exposure would actually matter.
            RoleView role = remoteOrderStaff();

            assertThat(role.canView(Section.CHART_OF_ACCOUNTS)).isFalse();
            assertThat(role.canView(Section.SETTINGS)).isFalse();
            assertThat(role.canView(Section.AUDIT_LOG)).isFalse();
            assertThat(role.canView(Section.TAX_AND_CHARGES)).isFalse();
            assertThat(role.canView(Section.USERS_AND_ROLES)).isFalse();
        }
    }

    @Nested
    @DisplayName("full-access roles")
    class FullAccess {

        @Test
        @DisplayName("see every section, including ones added later")
        void everySection() {
            RoleView role = fullAccessRole();

            for (Section section : Section.values()) {
                assertThat(role.accessTo(section)).isEqualTo(AccessLevel.FULL);
                assertThat(role.canEdit(section)).isTrue();
            }
            assertThat(role.visibleSections()).isEqualTo(EnumSet.allOf(Section.class));
        }

        @Test
        @DisplayName("hold no grants of their own, so a new section needs no migration")
        void noStoredGrants() {
            // The point of full_access being a flag rather than a row per section: with stored
            // grants, a section added in a later release would be invisible to the owner of the
            // system until someone remembered to insert one.
            assertThat(fullAccessRole().sectionGrants()).isEmpty();
        }

        @Test
        @DisplayName("see every protected field")
        void everyField() {
            RoleView role = fullAccessRole();

            for (ProtectedField field : ProtectedField.values()) {
                assertThat(role.canSee(field)).isTrue();
            }
            assertThat(role.hiddenFieldsIn(Section.PRODUCTS)).isEmpty();
        }
    }

    @Nested
    @DisplayName("layering rules")
    class Layering {

        @Test
        @DisplayName("field restrictions narrow section access and never widen it")
        void fieldRestrictionsCannotWiden() {
            // A role with no Products access at all, and no field restrictions either. The
            // absence of a restriction must not make the field visible.
            RoleView noProducts = new RoleView(
                    3L, "NO_PRODUCTS", null, false, false, true,
                    Map.of(Section.CUSTOMERS, AccessLevel.FULL), Set.of());

            assertThat(noProducts.canView(Section.PRODUCTS)).isFalse();
            assertThat(noProducts.canSee(ProtectedField.PRODUCT_LAST_PURCHASE_PRICE))
                    .as("a role that cannot see Products at all must not see a product's cost "
                            + "just because no restriction was recorded against the field")
                    .isFalse();
            // Nothing to redact in a response that will never be produced.
            assertThat(noProducts.hiddenFieldsIn(Section.PRODUCTS)).isEmpty();
        }

        @Test
        @DisplayName("view-only access is enough to see an unrestricted field")
        void viewAccessSuffices() {
            RoleView viewer = new RoleView(
                    4L, "VIEWER", null, false, false, true,
                    Map.of(Section.PRODUCTS, AccessLevel.VIEW),
                    Set.of(ProtectedField.PRODUCT_SUPPLIER));

            assertThat(viewer.canSee(ProtectedField.PRODUCT_LAST_PURCHASE_PRICE)).isTrue();
            assertThat(viewer.canSee(ProtectedField.PRODUCT_SUPPLIER)).isFalse();
        }

        @Test
        @DisplayName("an unlisted section is denied, not defaulted")
        void defaultDeny() {
            RoleView sparse = new RoleView(
                    5L, "SPARSE", null, false, false, true,
                    Map.of(Section.CUSTOMERS, AccessLevel.VIEW), Set.of());

            assertThat(sparse.accessTo(Section.CHART_OF_ACCOUNTS)).isEqualTo(AccessLevel.NONE);
        }
    }

    @Nested
    @DisplayName("an inactive role grants nothing")
    class Inactive {

        @Test
        @DisplayName("even a full-access role loses everything when deactivated")
        void inactiveFullAccessRole() {
            RoleView deactivated = new RoleView(
                    6L, "OWNER", null, true, true, false, Map.of(), Set.of());

            // Belt and braces alongside deactivating the user: disabling a role should not depend
            // on remembering to disable everyone who holds it.
            for (Section section : Section.values()) {
                assertThat(deactivated.accessTo(section)).isEqualTo(AccessLevel.NONE);
            }
            assertThat(deactivated.visibleSections()).isEmpty();
            assertThat(deactivated.canSee(ProtectedField.PRODUCT_SUPPLIER)).isFalse();
        }

        @Test
        @DisplayName("an inactive granted role loses its grants too")
        void inactiveGrantedRole() {
            RoleView deactivated = new RoleView(
                    7L, "REMOTE_ORDER_STAFF", null, false, false, false,
                    Map.of(Section.CUSTOMERS, AccessLevel.FULL), Set.of());

            assertThat(deactivated.canView(Section.CUSTOMERS)).isFalse();
        }
    }

    @Nested
    @DisplayName("the view is immutable")
    class Immutability {

        @Test
        @DisplayName("grants and restrictions cannot be modified through the view")
        void collectionsAreUnmodifiable() {
            RoleView role = remoteOrderStaff();

            // A permission set a caller can mutate is not a permission set.
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> role.sectionGrants()
                            .put(Section.SETTINGS, AccessLevel.FULL));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> role.restrictedFields()
                            .remove(ProtectedField.PRODUCT_SUPPLIER));
        }

        @Test
        @DisplayName("an empty restriction set is accepted")
        void emptyRestrictionsAreFine() {
            // EnumSet.copyOf rejects an empty non-EnumSet collection, so this is the case the
            // constructor works around.
            assertThat(new RoleView(8L, "EMPTY", null, false, false, true, Map.of(), Set.of())
                    .restrictedFields()).isEmpty();
        }
    }

    @Nested
    @DisplayName("sections declare whether they are built yet")
    class Availability {

        @Test
        @DisplayName("the sections built so far are marked available")
        void availableSections() {
            assertThat(Section.CHART_OF_ACCOUNTS.isAvailable()).isTrue();
            assertThat(Section.TAX_AND_CHARGES.isAvailable()).isTrue();
            assertThat(Section.SETTINGS.isAvailable()).isTrue();
            assertThat(Section.AUDIT_LOG.isAvailable()).isTrue();
            assertThat(Section.USERS_AND_ROLES.isAvailable()).isTrue();
            // Step 5.
            assertThat(Section.PRODUCTS.isAvailable()).isTrue();
            assertThat(Section.CUSTOMERS.isAvailable()).isTrue();
            assertThat(Section.SUPPLIERS.isAvailable()).isTrue();
            assertThat(Section.FIXED_ASSETS.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("sections whose features do not exist yet are marked reserved")
        void reservedSections() {
            // Distinguishing "you may not see this" from "this does not exist yet" — two states
            // that look identical to a user and have entirely different fixes.
            assertThat(Section.SALES_ORDER_FULFILLMENT.isAvailable()).isFalse();
            assertThat(Section.BACK_IN_STOCK_REMINDERS.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("every protected field belongs to a section")
        void fieldsHaveSections() {
            for (ProtectedField field : ProtectedField.values()) {
                assertThat(field.section()).isNotNull();
            }
        }
    }
}
