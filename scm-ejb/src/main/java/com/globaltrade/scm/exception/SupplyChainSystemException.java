package com.globaltrade.scm.exception;
public class SupplyChainSystemException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public SupplyChainSystemException(String message) {
        super(message);
    }
    public SupplyChainSystemException(String message, Throwable cause) {
        super(message, cause);
    }
}
