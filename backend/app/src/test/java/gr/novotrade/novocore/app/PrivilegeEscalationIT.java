package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.app.security.NovoCorePrincipal;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.InvalidRoleException;
import gr.novotrade.novocore.core.api.security.InvalidUserException;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.api.security.UserView;
import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * User administration must not be a route to unlimited access.
 *
 * <h2>The hole this closes, and why it needed two different rules</h2>
 *
 * <p>{@code USERS_AND_ROLES} is a grantable section rather than an Owner-only hard-coding (step 16b
 * proposal §1.5), which is what brief §7's "multiple custom roles from the start" requires. The
 * consequence is that a custom role can legitimately be given user administration — and every
 * individual thing such a role can then do is legitimate too. The <em>sequence</em> is not.
 *
 * <p>Two escapes exist and they are not the same shape, so they take different rules:
 *
 * <ol>
 *   <li><strong>Build a role and fill it.</strong> Create a second custom role, grant it
 *       {@code JOURNAL:FULL}, put an account in it, log in. Closed <em>per section</em> by
 *       {@code RoleServiceImpl.refuseIfCallerCannotConferIt}: you may only confer a level you hold.
 *       <strong>Gating this on the {@code fullAccess} flag would not have closed it</strong>, because
 *       the actor never touches a full-access role anywhere in the sequence — which is exactly what
 *       {@link #theFlagAloneWouldNotHaveCaughtTheCompoundPath()} exists to demonstrate.
 *   <li><strong>Use a role that already exists.</strong> Create an account directly in Owner or
 *       Admin and log in as it. Closed <em>by the flag</em> in
 *       {@code UserServiceImpl.refuseIfConferringFullAccessTheCallerLacks}. A per-section rule is
 *       the wrong instrument here: Owner and Admin hold no grant rows at all, so there are no
 *       per-section levels to compare against.
 * </ol>
 *
 * <p><strong>Asserted at the service layer with a real principal installed</strong>, rather than
 * over HTTP. The guards read {@code CurrentUser}, which resolves from the Spring Security context —
 * so installing a {@link NovoCorePrincipal} exercises the same code path a request does, and does
 * it before the users/roles routes exist to be driven. The routes get their own refusal tests.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + PrivilegeEscalationIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + PrivilegeEscalationIT.OWNER_PASSWORD,
        })
@Import(PostgresTestContainerConfiguration.class)
class PrivilegeEscalationIT {

    static final String OWNER_USERNAME = "escalation.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    private static final String PASSWORD = "a-password-long-enough";

    @Autowired private UserService users;
    @Autowired private RoleService roles;

    @AfterEach
    void clearTheSecurityContext() {
        // Leaking a principal into the next test would silently change which guard fires there.
        SecurityContextHolder.clearContext();
    }

    /**
     * The compound path, walked end to end as an attacker would.
     *
     * <p>Each step is asserted separately so the failure names <em>where</em> the sequence is cut,
     * not merely that it did not reach the end. That distinction is the whole point: a test asserting
     * only that the final account cannot be created would pass against a fix that lets the dangerous
     * role be built and populated, and merely refuses the last step.
     */
    @Test
    @DisplayName("a USERS_AND_ROLES-only role cannot build itself a way into the journal")
    void theCompoundPathIsCutAtTheGrant() {
        RoleView administrators = customRole("ESC_USER_ADMIN");
        roles.grant(administrators.id(), Section.USERS_AND_ROLES, AccessLevel.FULL);
        UserView actor = users.create(
                new NewUser("esc.useradmin", "User Admin", PASSWORD, administrators.id()));

        logInAs(actor);

        // Step 1 — creating a second role succeeds, and should. Creating a role is not the
        // dangerous act; a new role grants nothing at all (default-deny).
        RoleView target = roles.create(
                new NewRole("ESC_TARGET", "The role the actor is trying to fill"));
        assertThat(target.sectionGrants())
                .as("a newly created role grants nothing — that is what makes creating one safe")
                .isEmpty();

        // Step 2 — and this is where the sequence has to stop. The actor holds nothing on JOURNAL,
        // so they cannot put JOURNAL on anything.
        assertThatExceptionOfType(InvalidRoleException.class)
                .as("""
                        The grant is the step that must fail. Cutting the path only at account \
                        creation would leave a role carrying JOURNAL:FULL sitting in the database, \
                        one mistake away from somebody being moved into it.""")
                .isThrownBy(() -> roles.grant(target.id(), Section.JOURNAL, AccessLevel.FULL))
                .withMessageContaining("your own role has NONE there");

        // Step 3 — the role is genuinely still empty, not merely un-granted-in-passing. The refusal
        // has to have left no trace, or the escalation half-succeeded.
        assertThat(roles.require(target.id()).sectionGrants())
                .as("a refused grant must store nothing")
                .doesNotContainKey(Section.JOURNAL);

        // Step 4 — so an account created in it reaches nothing, which is the outcome that matters.
        UserView planted =
                users.create(new NewUser("esc.planted", "Planted", PASSWORD, target.id()));
        assertThat(planted.canView(Section.JOURNAL))
                .as("the whole point of the sequence was to reach the journal")
                .isFalse();
    }

    @Test
    @DisplayName("VIEW cannot be inflated into FULL either — the rule is per level, not per section")
    void aViewerCannotConferFull() {
        RoleView administrators = customRole("ESC_VIEWER_ADMIN");
        roles.grant(administrators.id(), Section.USERS_AND_ROLES, AccessLevel.FULL);
        roles.grant(administrators.id(), Section.CUSTOMERS, AccessLevel.VIEW);
        UserView actor = users.create(
                new NewUser("esc.vieweradmin", "Viewer Admin", PASSWORD, administrators.id()));

        logInAs(actor);

        RoleView target = roles.create(new NewRole("ESC_VIEW_TARGET", "Target"));

        assertThatExceptionOfType(InvalidRoleException.class)
                .as("holding VIEW is not authority to hand out FULL")
                .isThrownBy(() -> roles.grant(target.id(), Section.CUSTOMERS, AccessLevel.FULL))
                .withMessageContaining("your own role has VIEW there");

        // But passing on exactly what they hold is legitimate and must still work, or user
        // administration would be unable to do its job at all.
        assertThatCode(() -> roles.grant(target.id(), Section.CUSTOMERS, AccessLevel.VIEW))
                .doesNotThrowAnyException();
        assertThat(roles.require(target.id()).accessTo(Section.CUSTOMERS))
                .isEqualTo(AccessLevel.VIEW);
    }

    @Test
    @DisplayName("revoking never needs the access being revoked — de-escalation is not escalation")
    void revokingIsAlwaysAllowed() {
        RoleView administrators = customRole("ESC_REVOKER");
        roles.grant(administrators.id(), Section.USERS_AND_ROLES, AccessLevel.FULL);
        UserView actor =
                users.create(new NewUser("esc.revoker", "Revoker", PASSWORD, administrators.id()));

        RoleView target = roles.create(new NewRole("ESC_REVOKE_TARGET", "Has journal access"));
        roles.grant(target.id(), Section.JOURNAL, AccessLevel.FULL);

        logInAs(actor);

        assertThatCode(() -> roles.grant(target.id(), Section.JOURNAL, AccessLevel.NONE))
                .as("an administrator who cannot see the journal must still be able to take it "
                        + "away from somebody — refusing this would make containment need the very "
                        + "access being contained")
                .doesNotThrowAnyException();
        assertThat(roles.require(target.id()).canView(Section.JOURNAL)).isFalse();
    }

    @Nested
    @DisplayName("the second escape: an account in a role that is already full access")
    class TheFullAccessFlagGate {

        @Test
        @DisplayName("a non-full-access administrator cannot create an account in Owner or Admin")
        void cannotCreateAnAccountInAFullAccessRole() {
            RoleView administrators = customRole("ESC_PLANTER");
            roles.grant(administrators.id(), Section.USERS_AND_ROLES, AccessLevel.FULL);
            UserView actor = users.create(
                    new NewUser("esc.planter", "Planter", PASSWORD, administrators.id()));

            RoleView owner = roles.all().stream()
                    .filter(RoleView::fullAccess)
                    .findFirst()
                    .orElseThrow();

            logInAs(actor);

            assertThatExceptionOfType(InvalidUserException.class)
                    .as("""
                            RoleService.create already refuses to let a custom role BECOME full \
                            access. Without this, that refusal was trivially sidestepped: you \
                            cannot build a full-access role, but you could put an account in one \
                            of the two that already exist and log in as it.""")
                    .isThrownBy(() -> users.create(
                            new NewUser("esc.newowner", "New Owner", PASSWORD, owner.id())))
                    .withMessageContaining("full access to everything");
        }

        @Test
        @DisplayName("nor move an existing user into one")
        void cannotMoveAUserIntoAFullAccessRole() {
            RoleView administrators = customRole("ESC_PROMOTER");
            roles.grant(administrators.id(), Section.USERS_AND_ROLES, AccessLevel.FULL);
            UserView actor = users.create(
                    new NewUser("esc.promoter", "Promoter", PASSWORD, administrators.id()));

            RoleView nobody = customRole("ESC_NOBODY");
            UserView accomplice =
                    users.create(new NewUser("esc.accomplice", "Accomplice", PASSWORD, nobody.id()));
            RoleView owner = roles.all().stream()
                    .filter(RoleView::fullAccess)
                    .findFirst()
                    .orElseThrow();

            logInAs(actor);

            assertThatExceptionOfType(InvalidUserException.class)
                    .isThrownBy(() -> users.changeRole(accomplice.id(), owner.id()))
                    .withMessageContaining("full access to everything");
        }

        @Test
        @DisplayName("an actual administrator is unaffected — the guard must not break real work")
        void afullAccessActorMayStillDoIt() {
            UserView owner = users.findByUsername(OWNER_USERNAME).orElseThrow();
            RoleView ownerRole = owner.role();
            assertThat(ownerRole.fullAccess()).isTrue();

            logInAs(owner);

            assertThatCode(() -> users.create(
                    new NewUser("esc.secondowner", "Second Owner", PASSWORD, ownerRole.id())))
                    .as("a control that stops the Owner administering their own system is worse "
                            + "than the hole it closes")
                    .doesNotThrowAnyException();
        }
    }

    /**
     * The proof that the per-section rule is doing the work, in the manner the eviction tests set.
     *
     * <p>This does not re-run the compound test against a modified build — it states, and checks,
     * the specific fact that makes a flag-only fix insufficient: <strong>at no point in the compound
     * path does the actor touch a role flagged {@code fullAccess}</strong>. Every role involved is a
     * custom one. So a guard that only asks "is the target role full access?" answers no at every
     * step and lets the whole sequence through.
     *
     * <p>That was confirmed by removing {@code refuseIfCallerCannotConferIt} and re-running
     * {@link #theCompoundPathIsCutAtTheGrant}, which then failed at the grant step as expected. This
     * test is what keeps the reasoning checkable afterwards, since a build with the guard deleted is
     * not something the suite can hold onto.
     */
    @Test
    @DisplayName("the compound path never touches a full-access role, which is why the flag alone misses it")
    void theFlagAloneWouldNotHaveCaughtTheCompoundPath() {
        RoleView administrators = customRole("ESC_FLAG_PROOF_ADMIN");
        roles.grant(administrators.id(), Section.USERS_AND_ROLES, AccessLevel.FULL);
        UserView actor = users.create(
                new NewUser("esc.flagproof", "Flag Proof", PASSWORD, administrators.id()));

        logInAs(actor);
        RoleView target = roles.create(new NewRole("ESC_FLAG_PROOF_TARGET", "Target"));

        assertThat(actor.role().fullAccess())
                .as("the actor's own role is not full access")
                .isFalse();
        assertThat(target.fullAccess())
                .as("""
                        and neither is the role being built — RoleService.create cannot produce \
                        one. So a fullAccess-flag check has nothing to fire on anywhere in this \
                        sequence, and JOURNAL:FULL would have been conferred unopposed.""")
                .isFalse();

        // Which is precisely why the rule that stops it has to be about the section and the level.
        assertThatExceptionOfType(InvalidRoleException.class)
                .isThrownBy(() -> roles.grant(target.id(), Section.JOURNAL, AccessLevel.FULL));
    }

    // -------------------------------------------------------------------------------------------

    private RoleView customRole(String name) {
        return roles.findByName(name)
                .orElseGet(() -> roles.create(new NewRole(name, "Created by PrivilegeEscalationIT")));
    }

    /**
     * Installs a real {@link NovoCorePrincipal}, which is what {@code SecurityContextCurrentUser}
     * reads — so the guards see the same thing they would see during a request.
     */
    private void logInAs(UserView user) {
        NovoCorePrincipal principal = new NovoCorePrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
    }
}
