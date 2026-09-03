package com.globaltrade.scm.web.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.security.enterprise.credential.Credential;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStore;
import com.globaltrade.scm.security.SecurityUtil;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class SupplyChainIdentityStore implements IdentityStore {

    private static final String DATA_SOURCE_JNDI_NAME = "jdbc/SCMDataSource";
    private static final String AUTH_QUERY =
            "SELECT password_hash, role, active FROM system_user WHERE username = ?";

    @Override
    public CredentialValidationResult validate(Credential credential) {
        if (!(credential instanceof UsernamePasswordCredential)) {
            return CredentialValidationResult.NOT_VALIDATED_RESULT;
        }
        UsernamePasswordCredential userCredential = (UsernamePasswordCredential) credential;
        String username = userCredential.getCaller();
        String password = userCredential.getPasswordAsString();

        if (username == null || username.isBlank() || password == null) {
            return CredentialValidationResult.INVALID_RESULT;
        }

        try {
            InitialContext initialContext = new InitialContext();
            DataSource dataSource = (DataSource) initialContext.lookup(DATA_SOURCE_JNDI_NAME);
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(AUTH_QUERY)) {
                statement.setString(1, username);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        String passwordHash = resultSet.getString("password_hash");
                        String role = resultSet.getString("role");
                        boolean active = resultSet.getBoolean("active");

                        if (active && SecurityUtil.verifyPassword(password, passwordHash)) {
                            Set<String> roles = new HashSet<>();
                            roles.add(role);
                            return new CredentialValidationResult(username, roles);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception in IdentityStore: " + e.getMessage());
        }

        System.out.println("IdentityStore validation failed for user: " + username);
        return CredentialValidationResult.INVALID_RESULT;
    }
}
