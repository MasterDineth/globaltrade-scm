package com.globaltrade.scm.exception;
import jakarta.ejb.ApplicationException;
@ApplicationException(rollback = false)
public class CarrierSystemUnavailableException extends SupplyChainException {
    private static final long serialVersionUID = 1L;
    public CarrierSystemUnavailableException(String message) {
        super(message);
    }
    public CarrierSystemUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
