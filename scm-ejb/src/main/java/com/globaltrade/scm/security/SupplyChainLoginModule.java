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
public class SupplyChainLoginModule implements LoginModule {
    private static final String DATA_SOURCE_JNDI_NAME = "jdbc/SCMDataSource";
    private static final String AUTH_QUERY =
            "SELECT password_hash, role, active FROM system_user WHERE username = ?";
    private Subject subject;
    private CallbackHandler callbackHandler;
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
    private static final class UserRecord {
        String passwordHash;
        String role;
        boolean active;
    }
}
