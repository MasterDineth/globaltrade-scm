package com.globaltrade.scm.security;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import javax.sql.DataSource;
import java.io.IOException;
import java.security.Principal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Custom JAAS login module authenticating supply chain platform users
 * (logistics coordinators, customs agents, warehouse managers, vendor
 * representatives, customers, and administrators) against the
 * {@code system_user} table, satisfying the "JAAS integration with custom
 * login modules for supply chain authentication" technology requirement.
 *
 * <p><b>Deliberately implements the standard {@code javax.security.auth.spi.LoginModule}
 * SPI directly</b> (the five-method {@code initialize / login / commit /
 * abort / logout} contract specified by JSR 196's predecessor, JAAS
 * itself -- note this API lives under {@code javax.security.auth}, a core
 * JDK package, and is untouched by the {@code javax.*} &rarr; {@code jakarta.*}
 * rename that affected the EE platform APIs) rather than a
 * server-proprietary convenience base class. That is a deliberate
 * portability choice: this class has zero compile-time dependency on
 * GlassFish internals and would work unmodified under any JAAS-compliant
 * container. GlassFish-specific wiring -- registering this class against a
 * named {@code jaas-context} in {@code login.conf} / {@code domain.xml} and
 * mapping the resulting principals to {@code security-role}s in
 * {@code glassfish-ejb-jar.xml} / {@code glassfish-web.xml} -- is entirely
 * configuration, documented in {@code docs/DEPLOYMENT_GUIDE.md}, with no
 * code-level coupling.</p>
 *
 * <p>Deliberately uses raw JDBC against {@link javax.sql.DataSource}
 * looked up by JNDI rather than JPA/{@code EntityManager}: a login module
 * executes as part of the container's authentication pipeline, before any
 * EJB component's managed persistence context is guaranteed to be
 * available, and login modules are not themselves injectable EJB/CDI
 * components. Authenticating with the lightest-weight, most widely-supported
 * mechanism available at that layer is the correct engineering choice, not
 * a shortcut -- see {@code SystemUser}'s javadoc for the entity-side half
 * of this contract.</p>
 */
public class SupplyChainLoginModule implements LoginModule {

    private static final String DATA_SOURCE_JNDI_NAME = "jdbc/SCMDataSource";
    private static final String AUTH_QUERY =
            "SELECT password_hash, role, active FROM system_user WHERE username = ?";

    // Supplied by the container via initialize()
    private Subject subject;
    private CallbackHandler callbackHandler;

    // State carried between login() -> commit()/abort()
    private boolean loginSucceeded;
    private boolean committed;
    private String authenticatedUsername;
    private String assignedRole;
    private final Set<Principal> principalsAddedToSubject = new HashSet<>();

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler,
                            Map<String, ?> sharedState, Map<String, ?> options) {
        this.subject = subject;
        this.callbackHandler = callbackHandler;
    }

    /**
     * Phase 1 of the two-phase JAAS commit protocol: collect and verify
     * credentials, but do not yet modify the {@code Subject}. Returning
     * {@code false} (rather than throwing) would mean "this module chooses
     * to be ignored," which is not applicable here since this is the sole,
     * REQUIRED module in the supply chain realm's stack -- authentication
     * failure is always signalled via {@link LoginException}.
     */
    @Override
    public boolean login() throws LoginException {
        if (callbackHandler == null) {
            throw new LoginException("No CallbackHandler available to collect credentials.");
        }

        NameCallback nameCallback = new NameCallback("username: ");
        PasswordCallback passwordCallback = new PasswordCallback("password: ", false);
        try {
            callbackHandler.handle(new Callback[]{nameCallback, passwordCallback});
        } catch (IOException | UnsupportedCallbackException e) {
            throw (LoginException) new LoginException("Unable to collect login credentials.").initCause(e);
        }

        String submittedUsername = nameCallback.getName();
        char[] submittedPassword = passwordCallback.getPassword();
        passwordCallback.clearPassword();

        if (submittedUsername == null || submittedUsername.isBlank() || submittedPassword == null) {
            throw new LoginException("Username and password are required.");
        }

        try {
            UserRecord record = lookUpUser(submittedUsername);
            if (record == null || !record.active) {
                // Same failure message whether the account is unknown or
                // merely disabled: distinguishing the two to a would-be
                // attacker is a username-enumeration information leak.
                throw new LoginException("Authentication failed for user: " + submittedUsername);
            }
            if (!SecurityUtil.verifyPassword(new String(submittedPassword), record.passwordHash)) {
                throw new LoginException("Authentication failed for user: " + submittedUsername);
            }
            this.authenticatedUsername = submittedUsername;
            this.assignedRole = record.role;
            this.loginSucceeded = true;
            return true;
        } finally {
            Arrays.fill(submittedPassword, ' ');
        }
    }

    /**
     * Phase 2: called only after every {@code LoginModule} in the
     * configured stack has succeeded at phase 1. Populates the
     * {@code Subject} with a user-identity principal and a role principal,
     * which is what GlassFish's role-mapping layer subsequently uses to
     * satisfy {@code @RolesAllowed} / {@code isCallerInRole} checks.
     */
    @Override
    public boolean commit() throws LoginException {
        if (!loginSucceeded) {
            return false;
        }
        Principal userPrincipal = new SupplyChainPrincipal(authenticatedUsername);
        Principal rolePrincipal = new SupplyChainPrincipal(assignedRole);

        Set<Principal> subjectPrincipals = subject.getPrincipals();
        subjectPrincipals.add(userPrincipal);
        subjectPrincipals.add(rolePrincipal);
        principalsAddedToSubject.add(userPrincipal);
        principalsAddedToSubject.add(rolePrincipal);

        committed = true;
        return true;
    }

    /**
     * Called when phase 1 succeeded for this module but a later module in
     * the stack failed, so the overall login must be undone. Reverses
     * exactly what {@link #commit()} would have done (or already did).
     */
    @Override
    public boolean abort() throws LoginException {
        if (!loginSucceeded) {
            return false;
        }
        if (committed) {
            logout();
        } else {
            clearState();
        }
        return true;
    }

    @Override
    public boolean logout() throws LoginException {
        subject.getPrincipals().removeAll(principalsAddedToSubject);
        principalsAddedToSubject.clear();
        clearState();
        return true;
    }

    private void clearState() {
        loginSucceeded = false;
        committed = false;
        authenticatedUsername = null;
        assignedRole = null;
    }

    private UserRecord lookUpUser(String username) throws LoginException {
        try {
            DataSource dataSource = lookupDataSource();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(AUTH_QUERY)) {
                statement.setString(1, username);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return null;
                    }
                    UserRecord record = new UserRecord();
                    record.passwordHash = resultSet.getString("password_hash");
                    record.role = resultSet.getString("role");
                    record.active = resultSet.getBoolean("active");
                    return record;
                }
            }
        } catch (SQLException e) {
            throw (LoginException) new LoginException(
                    "Authentication store unavailable while authenticating user: " + username).initCause(e);
        }
    }

    private DataSource lookupDataSource() throws LoginException {
        try {
            InitialContext initialContext = new InitialContext();
            return (DataSource) initialContext.lookup(DATA_SOURCE_JNDI_NAME);
        } catch (NamingException e) {
            throw (LoginException) new LoginException(
                    "Unable to resolve JNDI resource " + DATA_SOURCE_JNDI_NAME).initCause(e);
        }
    }

    /** Package-private carrier for the single row read out of {@code system_user}. */
    private static final class UserRecord {
        String passwordHash;
        String role;
        boolean active;
    }
}
