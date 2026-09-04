package com.globaltrade.scm.exception;
import jakarta.ejb.ApplicationException;
@ApplicationException(rollback = false)
public class ShipmentTrackingException extends SupplyChainException {
    private static final long serialVersionUID = 1L;
    public ShipmentTrackingException(String message) {
        super(message);
    }
}
