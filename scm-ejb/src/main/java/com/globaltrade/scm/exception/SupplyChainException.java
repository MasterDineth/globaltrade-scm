package com.globaltrade.scm.exception;
public abstract class SupplyChainException extends Exception {
    private static final long serialVersionUID = 1L;
    protected SupplyChainException(String message) {
        super(message);
    }
    protected SupplyChainException(String message, Throwable cause) {
        super(message, cause);
    }
}
