package com.globaltrade.scm.exception;

/**
 * Root checked exception for expected, business-meaningful failures in the
 * supply chain domain. Checked application exceptions in EJB are the
 * correct tool when the *caller* is expected to catch and handle the
 * condition as part of normal control flow (insufficient stock, a
 * rejected customs filing, an unreachable carrier). Contrast with
 * {@link SupplyChainSystemException}, the unchecked sibling reserved for
 * failures the caller cannot meaningfully recover from.
 */
public abstract class SupplyChainException extends Exception {

    private static final long serialVersionUID = 1L;

    protected SupplyChainException(String message) {
        super(message);
    }

    protected SupplyChainException(String message, Throwable cause) {
        super(message, cause);
    }
}
