package com.globaltrade.scm.exception;
import jakarta.ejb.ApplicationException;
@ApplicationException(rollback = true)
public class CustomsComplianceException extends SupplyChainException {
    private static final long serialVersionUID = 1L;
    public CustomsComplianceException(String message) {
        super(message);
    }
}
