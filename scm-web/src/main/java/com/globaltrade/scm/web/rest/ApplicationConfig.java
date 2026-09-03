package com.globaltrade.scm.web.rest;

import jakarta.security.enterprise.authentication.mechanism.http.BasicAuthenticationMechanismDefinition;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import jakarta.annotation.security.DeclareRoles;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Activates JAX-RS under {@code /api/*} (see web.xml's matching
 * security-constraint url-pattern -- keep the two in sync if this path
 * ever changes). No {@code getClasses()}/{@code getSingletons()} override
 * is needed: classpath scanning discovers every {@code @Path} resource and
 * {@code @Provider} in this package automatically.
 */
@ApplicationScoped
@BasicAuthenticationMechanismDefinition
@DeclareRoles({"LOGISTICS_COORDINATOR", "SYSTEM_ADMIN"})
@ApplicationPath("/api")
public class ApplicationConfig extends Application {
}
