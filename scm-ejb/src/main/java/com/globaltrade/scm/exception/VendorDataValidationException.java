package com.globaltrade.scm.exception;
import jakarta.ejb.ApplicationException;
@ApplicationException(rollback = true)
public class VendorDataValidationException extends SupplyChainException {
    private static final long serialVersionUID = 1L;
    public VendorDataValidationException(String message) {
        super(message);
    }
}
