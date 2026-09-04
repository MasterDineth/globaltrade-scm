package com.globaltrade.scm.security;
import java.io.Serializable;
import java.security.Principal;
import java.util.Objects;
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
