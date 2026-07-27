package gr.novotrade.novocore.app.security;

import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.api.security.UserView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first Owner account, or refuses to start.
 *
 * <p><strong>There is no seeded default account and no default password.</strong> That is the same
 * stance the database credential already takes: an {@code admin}/{@code admin} shipped in a
 * migration outlives every intention to change it, and on a system holding the company's financial
 * records it is the single most likely way in.
 *
 * <p>So the first user comes from configuration, once, and the application <em>fails to start</em>
 * if no user exists and none is supplied. Failing is better than starting: an instance with no
 * accounts cannot be logged into, so it is useless either way, and a refusal that names the two
 * variables is a great deal easier to act on than a login page that rejects every password.
 *
 * <p>Runs only when the user table is empty. On every subsequent start it does nothing, so the
 * variables can be removed from the environment after the first boot — and should be, since
 * changing the password afterwards would not update them.
 */
@Component
class InitialOwnerBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialOwnerBootstrap.class);

    /** The seeded full-access system role from migration V6. */
    private static final String OWNER_ROLE = "OWNER";

    private final UserService users;
    private final RoleService roles;
    private final String username;
    private final String password;

    InitialOwnerBootstrap(
            UserService users,
            RoleService roles,
            @Value("${novocore.bootstrap.owner-username:}") String username,
            @Value("${novocore.bootstrap.owner-password:}") String password) {
        this.users = users;
        this.roles = roles;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!users.noUsersExist()) {
            // Already initialised. Deliberately silent about whether the variables are still set;
            // they are simply ignored from here on.
            return;
        }

        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("""
                    NovoCore has no user accounts and no initial owner was supplied, so nobody \
                    could log in. Set both of these and start again:

                      NOVOCORE_BOOTSTRAP_OWNER_USERNAME
                      NOVOCORE_BOOTSTRAP_OWNER_PASSWORD

                    There is deliberately no default account and no default password. Once the \
                    first owner exists these are ignored, and can be removed from the \
                    environment.""");
        }

        long ownerRoleId = roles.requireByName(OWNER_ROLE).id();
        UserView owner = users.create(
                new NewUser(username, "NovoCore Owner", password, ownerRoleId));

        // The username is logged; the password is not, and neither is its length.
        log.info("Created the initial owner account '{}'. The bootstrap variables are ignored "
                + "from now on and can be removed from the environment.", owner.username());
    }
}
