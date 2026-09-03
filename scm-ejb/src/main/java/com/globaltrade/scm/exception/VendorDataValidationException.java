package com.globaltrade.scm.exception;

import jakarta.ejb.ApplicationException;

/**
 * Thrown by {@code VendorDataValidationInterceptor} when inbound vendor
 * data fails validation before a business method is allowed to execute.
 * {@code rollback = true}: invalid vendor data must not be partially
 * persisted.
 */
@ApplicationException(rollback = true)
public class VendorDataValidationException extends SupplyChainException {

    private static final long serialVersionUID = 1L;

    public VendorDataValidationException(String message) {
        super(message);
    }
}
