package gr.novotrade.novocore.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.api.security.UserView;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The first-login bootstrap, including the case where it refuses to let the application start.
 *
 * <p>Mocked rather than run against a container: what is being tested is a decision — start, or
 * refuse and say why — and that decision needs no database to exercise. The database-backed half
 * of it is covered by {@code ChartOfAccountsEndpointIT}, which logs in as an owner the bootstrap
 * created.
 */
class InitialOwnerBootstrapTest {

    private static final RoleView OWNER_ROLE = new RoleView(
            7L, "OWNER", "Everything", true, true, true, Map.of(), Set.of());

    @Test
    @DisplayName("refuses to start when there are no users and no credentials were supplied")
    void refusesToStartWithoutCredentials() {
        UserService users = mock(UserService.class);
        RoleService roles = mock(RoleService.class);
        when(users.noUsersExist()).thenReturn(true);

        InitialOwnerBootstrap bootstrap = new InitialOwnerBootstrap(users, roles, "", "");

        // Failing beats starting: an instance with no accounts cannot be logged into, so it is
        // useless either way, and a refusal naming the variables is far easier to act on than a
        // login page that rejects every password.
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> bootstrap.run(null))
                .withMessageContaining("NOVOCORE_BOOTSTRAP_OWNER_USERNAME")
                .withMessageContaining("NOVOCORE_BOOTSTRAP_OWNER_PASSWORD")
                .withMessageContaining("no default account");

        verify(users, never()).create(any());
    }

    @Test
    @DisplayName("refuses when only one of the two is supplied")
    void refusesOnPartialCredentials() {
        UserService users = mock(UserService.class);
        RoleService roles = mock(RoleService.class);
        when(users.noUsersExist()).thenReturn(true);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() ->
                new InitialOwnerBootstrap(users, roles, "owner", "").run(null));
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() ->
                new InitialOwnerBootstrap(users, roles, "", "a-password-long-enough").run(null));
    }

    @Test
    @DisplayName("creates the owner in the OWNER role when the table is empty")
    void createsTheOwner() {
        UserService users = mock(UserService.class);
        RoleService roles = mock(RoleService.class);
        when(users.noUsersExist()).thenReturn(true);
        when(roles.requireByName("OWNER")).thenReturn(OWNER_ROLE);
        when(users.create(any())).thenReturn(new UserView(
                1L, "first.owner", "NovoCore Owner", OWNER_ROLE, true));

        new InitialOwnerBootstrap(users, roles, "first.owner", "a-password-long-enough")
                .run(null);

        ArgumentCaptor<NewUser> captor = ArgumentCaptor.forClass(NewUser.class);
        verify(users).create(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("first.owner");
        assertThat(captor.getValue().roleId()).isEqualTo(OWNER_ROLE.id());
        assertThat(captor.getValue().rawPassword()).isEqualTo("a-password-long-enough");
    }

    @Test
    @DisplayName("does nothing once any user exists, even with the variables still set")
    void doesNothingWhenUsersExist() {
        UserService users = mock(UserService.class);
        RoleService roles = mock(RoleService.class);
        when(users.noUsersExist()).thenReturn(false);

        new InitialOwnerBootstrap(users, roles, "someone", "a-password-long-enough").run(null);

        // Idempotent: the variables can be left in the environment without the account being
        // recreated or its password being reset on every restart.
        verify(users, never()).create(any());
        verify(roles, never()).requireByName(any());
    }
}
