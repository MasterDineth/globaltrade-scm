package com.globaltrade.scm.web.rest;
import jakarta.security.enterprise.authentication.mechanism.http.BasicAuthenticationMechanismDefinition;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import jakarta.annotation.security.DeclareRoles;
import jakarta.enterprise.context.ApplicationScoped;
@ApplicationScoped
@BasicAuthenticationMechanismDefinition
@DeclareRoles({"LOGISTICS_COORDINATOR", "SYSTEM_ADMIN"})
@ApplicationPath("/api")
public class ApplicationConfig extends Application {
}
