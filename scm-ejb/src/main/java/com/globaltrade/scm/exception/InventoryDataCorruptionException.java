package com.globaltrade.scm.exception;
public class InventoryDataCorruptionException extends SupplyChainSystemException {
    private static final long serialVersionUID = 1L;
    public InventoryDataCorruptionException(String message) {
        super(message);
    }
}
