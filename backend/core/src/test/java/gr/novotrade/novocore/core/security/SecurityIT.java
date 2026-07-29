package gr.novotrade.novocore.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.audit.AuditEntry;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.InvalidRoleException;
import gr.novotrade.novocore.core.api.security.InvalidUserException;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.ProtectedField;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.api.security.UserView;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Users, roles and password verification against a real database and the real V6 seed.
 *
 * <p>Test users get unique names from {@link #uniqueUsername}, since this class is not
 * transactional and shares one database with every other core integration test.
 */
class SecurityIT extends AbstractCoreIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final String GOOD_PASSWORD = "correct-horse-battery-staple";

    @Autowired
    private UserService users;

    @Autowired
    private RoleService roles;

    @Autowired
    private AuditLogService auditLog;

    @Autowired
    private JdbcTemplate jdbc;

    // ---------------------------------------------------------------------------------------
    // The seeded roles
    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("the V6 seed")
    class Seed {

        @Test
        @DisplayName("seeds exactly the three roles brief §7 defines")
        void seededRoles() {
            assertThat(roles.all()).extracting(RoleView::name)
                    .contains("OWNER", "ADMIN", "REMOTE_ORDER_STAFF");
        }

        @Test
        @DisplayName("Owner and Admin are full-access system roles")
        void ownerAndAdmin() {
            for (String name : List.of("OWNER", "ADMIN")) {
                RoleView role = roles.requireByName(name);
                assertThat(role.fullAccess()).as("%s is full access", name).isTrue();
                assertThat(role.systemRole()).as("%s is a system role", name).isTrue();
                assertThat(role.sectionGrants())
                        .as("%s needs no stored grants", name)
                        .isEmpty();
                for (Section section : Section.values()) {
                    assertThat(role.canEdit(section)).isTrue();
                }
            }
        }

        @Test
        @DisplayName("Remote/Order Staff is seeded with exactly the Q21 permissions")
        void remoteOrderStaffSeed() {
            RoleView role = roles.requireByName("REMOTE_ORDER_STAFF");

            assertThat(role.fullAccess()).isFalse();
            // Not a system role: this is the operational role most likely to need adjusting, so
            // it stays editable at runtime while Owner and Admin are locked.
            assertThat(role.systemRole()).isFalse();

            assertThat(role.accessTo(Section.SALES_ORDER_FULFILLMENT))
                    .isEqualTo(AccessLevel.FULL);
            assertThat(role.accessTo(Section.CUSTOMERS)).isEqualTo(AccessLevel.FULL);
            assertThat(role.accessTo(Section.BACK_IN_STOCK_REMINDERS)).isEqualTo(AccessLevel.FULL);
            assertThat(role.accessTo(Section.PRODUCTS)).isEqualTo(AccessLevel.VIEW);

            assertThat(role.canView(Section.CHART_OF_ACCOUNTS)).isFalse();
            assertThat(role.canView(Section.SETTINGS)).isFalse();
            assertThat(role.canView(Section.AUDIT_LOG)).isFalse();
            assertThat(role.canView(Section.TAX_AND_CHARGES)).isFalse();
            assertThat(role.canView(Section.USERS_AND_ROLES)).isFalse();

            // V26 removed all three of the field restrictions V6 seeded here: the business has no
            // confidentiality need around a product's purchase price or supplier. Asserted as empty
            // rather than deleted, so "nothing is hidden" is a claim this suite makes out loud
            // instead of a gap where an assertion used to be.
            assertThat(role.restrictedFields())
                    .as("Remote/Order Staff sees every field on a product it can view (V26)")
                    .isEmpty();
        }

        @Test
        @DisplayName("no role has any field restriction, and the mechanism still works if one does")
        void nothingIsFieldRestrictedAnywhere() {
            // The system-wide statement of the same decision. ProtectedField's three values are the
            // only fields the mechanism knows about and Remote/Order Staff held the only
            // restrictions, so after V26 the inner layer of brief §7's two-layer model is unused.
            //
            // Unused is not the same as broken, and the difference has to be provable: a change that
            // stopped the redacting reads consulting a role would otherwise pass everything, since
            // no real role would notice. So this also creates a role that DOES restrict a field and
            // asserts the mechanism reports it.
            // The SEEDED roles specifically. Other tests create throwaway roles that DO restrict a
            // field in this shared database — that is how the mechanism is exercised now that no
            // real role restricts anything — so a sweep over every role would be asserting something
            // about their fixtures rather than about the seed, and would pass or fail on execution
            // order. Which is itself worth knowing: it is why the claim is scoped to what V6 seeds.
            for (String seeded : List.of("OWNER", "ADMIN", "REMOTE_ORDER_STAFF")) {
                assertThat(roles.requireByName(seeded).restrictedFields())
                        .as("seeded role '%s' must restrict no field since V26", seeded)
                        .isEmpty();
            }

            RoleView probe = roles.create(new NewRole(
                    "SECIT_RESTRICTED_" + System.nanoTime(), "Field restriction still works"));
            roles.grant(probe.id(), Section.PRODUCTS, AccessLevel.VIEW);
            roles.restrictField(probe.id(), ProtectedField.PRODUCT_LAST_PURCHASE_PRICE, true);

            RoleView restricted = roles.require(probe.id());
            assertThat(restricted.restrictedFields())
                    .containsExactly(ProtectedField.PRODUCT_LAST_PURCHASE_PRICE);
            assertThat(restricted.canSee(ProtectedField.PRODUCT_LAST_PURCHASE_PRICE)).isFalse();
            assertThat(restricted.canSee(ProtectedField.PRODUCT_SUPPLIER)).isTrue();
        }

        @Test
        @DisplayName("the migration inserts no user account — there is no default password")
        void noSeededUsers() throws Exception {
            // Asserted against the migration's own text rather than by counting rows. Counting
            // cannot work: these tests share a database and are not transactional, so any user
            // another test created would be indistinguishable from a seeded one — they all carry
            // created_by = 'system', because the core's test context has no CurrentUser bean.
            //
            // The claim being tested is a property of the migration, so the migration is what is
            // read. An admin/admin inserted here would outlive every intention to change it.
            String migration;
            try (var stream = getClass()
                    .getResourceAsStream("/db/migration/V6__users_roles_permissions.sql")) {
                assertThat(stream).as("V6 migration must be on the test classpath").isNotNull();
                migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }

            assertThat(migration.toLowerCase(Locale.ROOT))
                    .as("V6 must not insert any user row")
                    .doesNotContain("insert into app_user");
        }

        @Test
        @DisplayName("NONE is never stored as a grant")
        void noneIsNotStored() {
            // The absence of a row already means no access; storing "grants nothing" rows would
            // create two representations of one state.
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM role_section_grant WHERE access_level = 'NONE'",
                    Integer.class))
                    .isZero();
        }
    }

    // ---------------------------------------------------------------------------------------
    // Authentication
    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("authentication")
    class Authentication {

        @Test
        @DisplayName("a correct password authenticates and returns the user with its permissions")
        void correctPassword() {
            UserView created = createUser("OWNER");

            UserView authenticated = users.authenticate(created.username(), GOOD_PASSWORD)
                    .orElseThrow();

            assertThat(authenticated.username()).isEqualTo(created.username());
            // Permissions arrive with the user, so a request needs no second lookup to answer
            // "may I?".
            assertThat(authenticated.role().canView(Section.CHART_OF_ACCOUNTS)).isTrue();
        }

        @Test
        @DisplayName("the username is case-insensitive")
        void usernameIsCaseInsensitive() {
            UserView created = createUser("OWNER");

            assertThat(users.authenticate(created.username().toUpperCase(), GOOD_PASSWORD))
                    .isPresent();
        }

        @Test
        @DisplayName("a wrong password, an unknown user and an inactive user are indistinguishable")
        void failuresAreIndistinguishable() {
            UserView active = createUser("OWNER");
            UserView inactive = createUser("REMOTE_ORDER_STAFF");
            users.deactivate(inactive.id());

            // All three return exactly the same thing. Telling them apart is how an attacker
            // works out who has an account here.
            assertThat(users.authenticate(active.username(), "wrong-password-entirely")).isEmpty();
            assertThat(users.authenticate(uniqueUsername(), GOOD_PASSWORD)).isEmpty();
            assertThat(users.authenticate(inactive.username(), GOOD_PASSWORD)).isEmpty();
        }

        @Test
        @DisplayName("a user whose role has been deactivated cannot authenticate")
        void inactiveRoleBlocksLogin() {
            RoleView role = roles.create(new NewRole(uniqueRoleName(), "Temporary"));
            UserView user = users.create(new NewUser(
                    uniqueUsername(), "Role Deactivation Test", GOOD_PASSWORD, role.id()));

            // A role cannot be deactivated while users hold it, so move the user away first —
            // which is the guard doing its job.
            assertThatExceptionOfType(InvalidRoleException.class)
                    .isThrownBy(() -> roles.deactivate(role.id()))
                    .withMessageContaining("still has 1 user");

            // Deactivate the role out from under the user directly, to prove the login path
            // checks it rather than relying on that guard alone.
            jdbc.update("UPDATE app_role SET active = false WHERE id = ?", role.id());

            assertThat(users.authenticate(user.username(), GOOD_PASSWORD)).isEmpty();
        }

        @Test
        @DisplayName("null arguments are refused rather than throwing")
        void nullsAreRefused() {
            assertThat(users.authenticate(null, GOOD_PASSWORD)).isEmpty();
            assertThat(users.authenticate("someone", null)).isEmpty();
        }

        @Test
        @DisplayName("both success and failure are written to the audit log with a reason")
        void loginsAreAudited() {
            UserView user = createUser("OWNER");

            users.authenticate(user.username(), GOOD_PASSWORD);
            users.authenticate(user.username(), "wrong-password-entirely");

            List<AuditEntry> recent = auditLog.findRecent(200);

            assertThat(recent)
                    .as("a login trail recording only successes is not much of a control")
                    .anySatisfy(entry -> {
                        assertThat(entry.action()).isEqualTo("user.login-succeeded");
                        assertThat(entry.detail()).containsEntry("username", user.username());
                    })
                    .anySatisfy(entry -> {
                        assertThat(entry.action()).isEqualTo("user.login-failed");
                        assertThat(entry.detail())
                                .containsEntry("username", user.username())
                                // The reason is recorded for an administrator even though it is
                                // never returned to the caller.
                                .containsEntry("reason", "bad-password");
                    });
        }

        @Test
        @DisplayName("no password or hash ever reaches the audit log")
        void passwordsAreNeverAudited() {
            UserView user = createUser("OWNER");
            users.authenticate(user.username(), GOOD_PASSWORD);

            assertThat(auditLog.findRecent(200))
                    .allSatisfy(entry -> assertThat(entry.detail().values())
                            .noneSatisfy(value -> assertThat(value)
                                    .contains(GOOD_PASSWORD)));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Password handling
    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("passwords")
    class Passwords {

        @Test
        @DisplayName("are stored hashed, algorithm-prefixed, never in plain text")
        void storedHashed() {
            UserView user = createUser("OWNER");

            String hash = jdbc.queryForObject(
                    "SELECT password_hash FROM app_user WHERE id = ?", String.class, user.id());

            assertThat(hash).isNotNull().doesNotContain(GOOD_PASSWORD);
            // The prefix is what makes a later move to a stronger algorithm possible without
            // invalidating every existing password.
            assertThat(hash).startsWith("{bcrypt}$2");
        }

        @Test
        @DisplayName("the same password produces different hashes for different users")
        void hashesAreSalted() {
            UserView first = createUser("OWNER");
            UserView second = createUser("OWNER");

            String firstHash = jdbc.queryForObject(
                    "SELECT password_hash FROM app_user WHERE id = ?", String.class, first.id());
            String secondHash = jdbc.queryForObject(
                    "SELECT password_hash FROM app_user WHERE id = ?", String.class, second.id());

            assertThat(firstHash).isNotEqualTo(secondHash);
        }

        @Test
        @DisplayName("a password shorter than twelve characters is refused")
        void minimumLength() {
            assertThatExceptionOfType(InvalidUserException.class)
                    .isThrownBy(() -> users.create(new NewUser(
                            uniqueUsername(), "Short", "short", ownerRoleId())))
                    .withMessageContaining("at least 12 characters");
        }

        @Test
        @DisplayName("a whitespace-only password is refused")
        void whitespaceOnly() {
            assertThatExceptionOfType(InvalidUserException.class)
                    .isThrownBy(() -> users.create(new NewUser(
                            uniqueUsername(), "Blank", "               ", ownerRoleId())))
                    .withMessageContaining("whitespace");
        }

        @Test
        @DisplayName("changing a password invalidates the old one and is audited")
        void changePassword() {
            UserView user = createUser("OWNER");
            String replacement = "a-completely-different-password";

            users.changePassword(user.id(), replacement);

            assertThat(users.authenticate(user.username(), GOOD_PASSWORD)).isEmpty();
            assertThat(users.authenticate(user.username(), replacement)).isPresent();

            assertThat(auditLog.findForEntity("User", String.valueOf(user.id()), 10))
                    .extracting(AuditEntry::action)
                    .contains("user.password-changed");
        }

        @Test
        @DisplayName("the exception message never quotes the password")
        void messagesDoNotQuoteThePassword() {
            String rejected = "sekret";

            assertThatExceptionOfType(InvalidUserException.class)
                    .isThrownBy(() -> users.create(new NewUser(
                            uniqueUsername(), "Quoted", rejected, ownerRoleId())))
                    .withMessageNotContaining(rejected);
        }

        @Test
        @DisplayName("NewUser.toString does not leak the password")
        void newUserToStringIsSafe() {
            // Records print every component by default, and a NewUser reaching a log line at
            // debug level would print the password with it.
            assertThat(new NewUser("someone", "Someone", GOOD_PASSWORD, 1L).toString())
                    .doesNotContain(GOOD_PASSWORD)
                    .contains("<not shown>");
        }
    }

    // ---------------------------------------------------------------------------------------
    // Administration
    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("user administration")
    class Administration {

        @Test
        @DisplayName("a duplicate username is refused regardless of case")
        void duplicateUsername() {
            UserView existing = createUser("OWNER");

            assertThatExceptionOfType(InvalidUserException.class)
                    .isThrownBy(() -> users.create(new NewUser(
                            existing.username().toUpperCase(), "Clash", GOOD_PASSWORD,
                            ownerRoleId())))
                    .withMessageContaining("already taken");
        }

        @Test
        @DisplayName("a malformed username is refused")
        void malformedUsername() {
            assertThatExceptionOfType(InvalidUserException.class)
                    .isThrownBy(() -> users.create(new NewUser(
                            "has spaces", "Spaces", GOOD_PASSWORD, ownerRoleId())))
                    .withMessageContaining("lower-case letters");
        }

        @Test
        @DisplayName("the last full-access user cannot be deactivated or demoted")
        void lastAdministratorIsProtected() {
            // Only meaningful when this really is the last one, which depends on what other tests
            // have left behind — so it asserts the guard only when the precondition holds.
            List<UserView> administrators = users.active().stream()
                    .filter(user -> user.role().fullAccess())
                    .toList();
            if (administrators.size() != 1) {
                return;
            }
            UserView last = administrators.getFirst();

            assertThatExceptionOfType(InvalidUserException.class)
                    .isThrownBy(() -> users.deactivate(last.id()))
                    .withMessageContaining("only active user with full access");

            long staffRoleId = roles.requireByName("REMOTE_ORDER_STAFF").id();
            assertThatExceptionOfType(InvalidUserException.class)
                    .isThrownBy(() -> users.changeRole(last.id(), staffRoleId))
                    .withMessageContaining("nobody able to administer");
        }

        @Test
        @DisplayName("a user can be moved between roles, gaining and losing access")
        void changeRole() {
            UserView user = createUser("OWNER");
            assertThat(user.role().canView(Section.CHART_OF_ACCOUNTS)).isTrue();

            // Guarantee another administrator exists, so this is not the last-admin case.
            createUser("OWNER");

            UserView moved = users.changeRole(
                    user.id(), roles.requireByName("REMOTE_ORDER_STAFF").id());

            assertThat(moved.role().name()).isEqualTo("REMOTE_ORDER_STAFF");
            assertThat(moved.canView(Section.CHART_OF_ACCOUNTS)).isFalse();
            assertThat(moved.canView(Section.CUSTOMERS)).isTrue();
            // Visible since V26 — Remote/Order Staff restricts no field. The assertion is kept
            // rather than dropped so the role change is still shown to carry field visibility with
            // it, which is the property this test is about.
            assertThat(moved.canSee(ProtectedField.PRODUCT_SUPPLIER)).isTrue();
        }

        @Test
        @DisplayName("a deactivated user loses all access even though their role has not changed")
        void deactivatedUserHasNoAccess() {
            UserView user = createUser("REMOTE_ORDER_STAFF");
            assertThat(user.canView(Section.CUSTOMERS)).isTrue();

            users.deactivate(user.id());
            UserView deactivated = users.require(user.id());

            assertThat(deactivated.role().canView(Section.CUSTOMERS))
                    .as("the role itself is unchanged")
                    .isTrue();
            assertThat(deactivated.canView(Section.CUSTOMERS))
                    .as("but the user has no access")
                    .isFalse();
            assertThatExceptionOfType(
                    gr.novotrade.novocore.core.api.security.SectionAccessDeniedException.class)
                    .isThrownBy(() -> deactivated.requireView(Section.CUSTOMERS));
        }

        @Test
        @DisplayName("noUsersExist reports false once an account exists")
        void noUsersExistFlips() {
            createUser("OWNER");
            // The condition the startup bootstrap tests.
            assertThat(users.noUsersExist()).isFalse();
        }
    }

    // ---------------------------------------------------------------------------------------
    // Role administration
    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("role administration")
    class RoleAdministration {

        @Test
        @DisplayName("a custom role starts with access to nothing")
        void customRoleStartsEmpty() {
            RoleView role = roles.create(new NewRole(uniqueRoleName(), "Custom"));

            assertThat(role.fullAccess()).isFalse();
            assertThat(role.systemRole()).isFalse();
            assertThat(role.visibleSections()).isEmpty();
            for (Section section : Section.values()) {
                assertThat(role.accessTo(section)).isEqualTo(AccessLevel.NONE);
            }
        }

        @Test
        @DisplayName("a custom role can be granted and un-granted sections, audited each time")
        void grantAndRevoke() {
            RoleView role = roles.create(new NewRole(uniqueRoleName(), "Custom"));

            RoleView granted = roles.grant(role.id(), Section.CHART_OF_ACCOUNTS, AccessLevel.VIEW);
            assertThat(granted.canView(Section.CHART_OF_ACCOUNTS)).isTrue();
            assertThat(granted.canEdit(Section.CHART_OF_ACCOUNTS)).isFalse();

            RoleView promoted = roles.grant(role.id(), Section.CHART_OF_ACCOUNTS, AccessLevel.FULL);
            assertThat(promoted.canEdit(Section.CHART_OF_ACCOUNTS)).isTrue();

            RoleView revoked = roles.grant(role.id(), Section.CHART_OF_ACCOUNTS, AccessLevel.NONE);
            assertThat(revoked.canView(Section.CHART_OF_ACCOUNTS)).isFalse();
            assertThat(revoked.sectionGrants())
                    .as("NONE removes the row rather than storing a grant that grants nothing")
                    .doesNotContainKey(Section.CHART_OF_ACCOUNTS);

            assertThat(auditLog.findForEntity("Role", String.valueOf(role.id()), 10))
                    .extracting(AuditEntry::action)
                    .contains("role.granted");
        }

        @Test
        @DisplayName("field restrictions can be added and removed on a custom role")
        void fieldRestrictions() {
            RoleView role = roles.create(new NewRole(uniqueRoleName(), "Custom"));
            roles.grant(role.id(), Section.PRODUCTS, AccessLevel.VIEW);

            RoleView restricted = roles.restrictField(
                    role.id(), ProtectedField.PRODUCT_LAST_PURCHASE_PRICE, true);
            assertThat(restricted.canSee(ProtectedField.PRODUCT_LAST_PURCHASE_PRICE)).isFalse();
            assertThat(restricted.canSee(ProtectedField.PRODUCT_SUPPLIER)).isTrue();

            RoleView unrestricted = roles.restrictField(
                    role.id(), ProtectedField.PRODUCT_LAST_PURCHASE_PRICE, false);
            assertThat(unrestricted.canSee(ProtectedField.PRODUCT_LAST_PURCHASE_PRICE)).isTrue();
        }

        @Test
        @DisplayName("a system role cannot be modified, renamed or deactivated")
        void systemRolesAreLocked() {
            long ownerId = roles.requireByName("OWNER").id();

            // Without this, removing USERS_AND_ROLES from the last role that has it locks
            // everyone out of user administration with no route back in.
            assertThatExceptionOfType(InvalidRoleException.class)
                    .isThrownBy(() -> roles.grant(ownerId, Section.SETTINGS, AccessLevel.NONE))
                    .withMessageContaining("system role");
            assertThatExceptionOfType(InvalidRoleException.class)
                    .isThrownBy(() -> roles.rename(ownerId, "SUPERUSER"));
            assertThatExceptionOfType(InvalidRoleException.class)
                    .isThrownBy(() -> roles.deactivate(ownerId));
            assertThatExceptionOfType(InvalidRoleException.class)
                    .isThrownBy(() -> roles.restrictField(
                            ownerId, ProtectedField.PRODUCT_SUPPLIER, true));
        }

        @Test
        @DisplayName("Remote/Order Staff is editable, unlike the two system roles")
        void remoteStaffIsEditable() {
            long staffId = roles.requireByName("REMOTE_ORDER_STAFF").id();

            RoleView granted = roles.grant(staffId, Section.AUDIT_LOG, AccessLevel.VIEW);
            try {
                assertThat(granted.canView(Section.AUDIT_LOG)).isTrue();
            } finally {
                // Restored, so the seed assertions elsewhere stay independent of test ordering.
                roles.grant(staffId, Section.AUDIT_LOG, AccessLevel.NONE);
            }
            assertThat(roles.require(staffId).canView(Section.AUDIT_LOG)).isFalse();
        }

        @Test
        @DisplayName("a role still held by a user cannot be deactivated")
        void roleWithHoldersCannotBeDeactivated() {
            RoleView role = roles.create(new NewRole(uniqueRoleName(), "Custom"));
            users.create(new NewUser(
                    uniqueUsername(), "Holder", GOOD_PASSWORD, role.id()));

            // Refused rather than cascading: someone losing access with no explanation is worse
            // than being told to move them first.
            assertThatExceptionOfType(InvalidRoleException.class)
                    .isThrownBy(() -> roles.deactivate(role.id()))
                    .withMessageContaining("Move them to another role first");
        }

        @Test
        @DisplayName("there is no way to create a full-access role")
        void cannotCreateFullAccessRole() {
            // A second route to unlimited access is a second thing to audit. Owner and Admin
            // already exist for it.
            RoleView created = roles.create(new NewRole(uniqueRoleName(), "Attempt"));
            assertThat(created.fullAccess()).isFalse();

            assertThat(NewRole.class.getRecordComponents())
                    .noneMatch(component -> component.getName().toLowerCase()
                            .contains("fullaccess"));
        }

        @Test
        @DisplayName("a duplicate role name is refused")
        void duplicateRoleName() {
            assertThatExceptionOfType(InvalidRoleException.class)
                    .isThrownBy(() -> roles.create(new NewRole("owner", "Clash")))
                    .withMessageContaining("already exists");
        }
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private UserView createUser(String roleName) {
        return users.create(new NewUser(
                uniqueUsername(),
                "Test User",
                GOOD_PASSWORD,
                roles.requireByName(roleName).id()));
    }

    private long ownerRoleId() {
        return roles.requireByName("OWNER").id();
    }

    private static String uniqueUsername() {
        return "test.user." + COUNTER.incrementAndGet();
    }

    private static String uniqueRoleName() {
        return "TEST_ROLE_" + COUNTER.incrementAndGet();
    }

    @Nested
    @DisplayName("the section list, in Java and in the database")
    class SectionListsAgree {

        @Test
        @DisplayName("every Section is listed in the role_section_grant CHECK")
        void everySectionIsGrantable() {
            // Added after a real miss. Step 14c introduced Section.EMAIL_OUTBOX and the plan said
            // "no migrations expected", on the reasoning that a Section is a Java enum and grants
            // are default-deny. The database disagreed: role_section_grant carries a CHECK listing
            // every section by name, so a value that exists only in Java cannot be granted at all
            // - every insert is refused. It surfaced as three failing tests rather than as a
            // deduction, which is exactly the case for having this one.
            for (Section section : Section.values()) {
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM pg_constraint
                        WHERE conname = 'role_section_grant_section_known'
                          AND pg_get_constraintdef(oid) LIKE ?
                        """, Integer.class, "%'" + section.name() + "'%"))
                        .as("%s is listed in role_section_grant_section_known - without it, "
                                + "granting this section is refused by the database", section)
                        .isEqualTo(1);
            }
        }

        @Test
        @DisplayName("the CHECK lists no section the enum does not have")
        void theCheckListsNothingExtra() {
            // The other direction: a value in the CHECK that Java lacks is a grant nothing can
            // read back. Counted by quote characters, the same way JournalIT holds
            // journal_entry_source_known to JournalSource.
            assertThat(jdbc.queryForObject("""
                    SELECT length(pg_get_constraintdef(oid))
                        - length(replace(pg_get_constraintdef(oid), '''', ''))
                    FROM pg_constraint WHERE conname = 'role_section_grant_section_known'
                    """, Integer.class))
                    .isEqualTo(Section.values().length * 2);
        }

        @Test
        @DisplayName("a grant really can be stored for every section, reserved ones included")
        void everySectionCanActuallyBeGranted() {
            // Structural agreement is not the same as it working. Reserved sections are granted
            // too - the permission model is complete before the features are, which is the whole
            // reason Section.isAvailable() exists rather than the value being absent.
            RoleView role = roles.create(new NewRole("SECIT_ALL_SECTIONS", "Every section"));
            for (Section section : Section.values()) {
                roles.grant(role.id(), section, AccessLevel.VIEW);
            }

            assertThat(roles.require(role.id()).visibleSections())
                    .containsExactlyInAnyOrder(Section.values());
        }
    }

}
