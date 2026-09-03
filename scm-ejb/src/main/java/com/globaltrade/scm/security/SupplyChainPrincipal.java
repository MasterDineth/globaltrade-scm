package com.globaltrade.scm.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.Objects;

/**
 * Minimal {@link Principal} implementation added to the JAAS {@code Subject}
 * by {@link SupplyChainLoginModule} -- one instance for the authenticated
 * user's own identity, and a second instance (same class, different name)
 * for their role. GlassFish's principal-to-role mapping
 * (see {@code glassfish-application.xml}'s {@code <security-role-mapping>})
 * resolves {@code @RolesAllowed} / {@code <security-role>} checks against
 * the names of principals already present in the authenticated Subject, so
 * one plain {@code Principal} type serving double duty as both "user
 * identity" and "role membership" is sufficient -- no separate marker type
 * is required for the single-role-per-user model this application uses
 * (see {@code SystemUser.role}).
 *
 * <p><b>This class deliberately does *not* implement {@code java.security.acl.Group}</b>,
 * despite that being the historically-documented pattern for a JAAS role
 * principal on GlassFish. An earlier revision of this class did exactly
 * that, on the assumption -- copied from older GlassFish/JAAS reference
 * material without independently verifying it against a current JDK -- that
 * {@code java.security.acl.Group} was still a normal, if deprecated, part of
 * the platform. It is not: {@code java.security.acl} (Group, Acl, AclEntry,
 * Owner, Permission) has been fully removed from the JDK's module system,
 * not merely deprecated, since Java 9, confirmed here by compiling a
 * minimal reproduction against a real JDK 21 with no special module flags
 * (`package java.security.acl does not exist`). Since GlassFish 7 itself
 * requires a JDK new enough to have long since dropped that package, no
 * current GlassFish version can be relying on the JDK's own
 * {@code java.security.acl.Group} for role recognition either -- whatever
 * internal mechanism it actually uses is GlassFish-proprietary. Rather than
 * guess at (and introduce a compile-time dependency on) that internal type,
 * this class stays a plain, portable {@code Principal}, and role mapping is
 * expressed via {@code <security-role-mapping><principal-name>} in
 * {@code glassfish-application.xml} rather than {@code <group-name>}, which
 * does not require the Subject to contain any particular marker type to
 * begin with. This correction was caught by actually compiling this module
 * standalone against a real JDK during test-report preparation, rather than
 * by code review alone -- see docs/CRITICAL_ANALYSIS.md's "Component
 * organization and deployment strategy" section and the project's test
 * report for the fuller account.</p>
 */
public final class SupplyChainPrincipal implements Principal, Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;

    public SupplyChainPrincipal(String name) {
        this.name = Objects.requireNonNull(name, "principal name must not be null");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SupplyChainPrincipal)) {
            return false;
        }
        return name.equals(((SupplyChainPrincipal) other).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "SupplyChainPrincipal[" + name + "]";
    }
}
