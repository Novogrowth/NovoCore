package gr.novotrade.novocore.core.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@link AccessLevel} as pure logic — in particular {@link AccessLevel#isNarrowerThan}, which
 * decides whether a role change ends its holders' sessions.
 *
 * <p>Worth testing exhaustively rather than by example: there are nine ordered pairs and the whole
 * question is which of them mean "access was taken away". A wrong answer in either direction is a
 * real defect — one leaves a revoked user logged in, the other logs people out for being given more.
 */
class AccessLevelTest {

    @Nested
    @DisplayName("isNarrowerThan")
    class IsNarrowerThan {

        @Test
        @DisplayName("all nine pairs, stated exhaustively rather than by rule")
        void allNinePairs() {
            // Written out in full on purpose. Deriving the expectation from rank() would be the
            // same mistake PermissionSweepIT names about reading a permission expectation back off
            // the declaration it is meant to be checking.
            assertThat(AccessLevel.NONE.isNarrowerThan(AccessLevel.FULL)).isTrue();
            assertThat(AccessLevel.NONE.isNarrowerThan(AccessLevel.VIEW)).isTrue();
            assertThat(AccessLevel.VIEW.isNarrowerThan(AccessLevel.FULL)).isTrue();

            assertThat(AccessLevel.FULL.isNarrowerThan(AccessLevel.NONE)).isFalse();
            assertThat(AccessLevel.FULL.isNarrowerThan(AccessLevel.VIEW)).isFalse();
            assertThat(AccessLevel.VIEW.isNarrowerThan(AccessLevel.NONE)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(AccessLevel.class)
        @DisplayName("a level is never narrower than itself — a no-op grant must not evict anybody")
        void neverNarrowerThanItself(AccessLevel level) {
            // The case that would bite in practice: an administrator re-saving a role editor form
            // without changing anything, logging out everybody holding that role for no reason.
            assertThat(level.isNarrowerThan(level)).isFalse();
        }

        @Test
        @DisplayName("the ordering is NONE < VIEW < FULL and does not depend on declaration order")
        void orderingIsExplicit() {
            // rank() is a switch rather than ordinal() precisely so that reordering the constants
            // cannot silently change who stays logged in. This asserts the ordering the switch
            // states, so a change to it is a deliberate act with a failing test behind it.
            List<AccessLevel> wideningOrder =
                    List.of(AccessLevel.NONE, AccessLevel.VIEW, AccessLevel.FULL);

            for (int narrower = 0; narrower < wideningOrder.size(); narrower++) {
                for (int wider = narrower + 1; wider < wideningOrder.size(); wider++) {
                    assertThat(wideningOrder.get(narrower).isNarrowerThan(wideningOrder.get(wider)))
                            .as("%s should be narrower than %s",
                                    wideningOrder.get(narrower), wideningOrder.get(wider))
                            .isTrue();
                    assertThat(wideningOrder.get(wider).isNarrowerThan(wideningOrder.get(narrower)))
                            .as("%s should not be narrower than %s",
                                    wideningOrder.get(wider), wideningOrder.get(narrower))
                            .isFalse();
                }
            }
        }
    }

    @Nested
    @DisplayName("allowsView / allowsEdit")
    class Allows {

        @Test
        @DisplayName("NONE allows nothing, VIEW reads only, FULL does both")
        void theThreeLevels() {
            assertThat(AccessLevel.NONE.allowsView()).isFalse();
            assertThat(AccessLevel.NONE.allowsEdit()).isFalse();

            assertThat(AccessLevel.VIEW.allowsView()).isTrue();
            assertThat(AccessLevel.VIEW.allowsEdit()).isFalse();

            assertThat(AccessLevel.FULL.allowsView()).isTrue();
            assertThat(AccessLevel.FULL.allowsEdit()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(AccessLevel.class)
        @DisplayName("anything that allows editing allows viewing")
        void editImpliesView(AccessLevel level) {
            // Not currently violable, and worth pinning: a level that could change a section
            // without being able to see it would make every "can they read this?" check unsound.
            if (level.allowsEdit()) {
                assertThat(level.allowsView()).isTrue();
            }
        }
    }
}
