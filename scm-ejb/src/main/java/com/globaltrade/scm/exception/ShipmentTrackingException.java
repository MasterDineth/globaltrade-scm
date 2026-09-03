package com.globaltrade.scm.exception;

import jakarta.ejb.ApplicationException;

/**
 * Thrown for tracking-lookup failures that are the caller's fault (unknown
 * tracking number) rather than a system problem. {@code rollback = false}:
 * a "not found" on a read-only lookup has nothing to roll back and should
 * not be treated as a failure of the surrounding unit of work.
 */
@ApplicationException(rollback = false)
public class ShipmentTrackingException extends SupplyChainException {

    private static final long serialVersionUID = 1L;

    public ShipmentTrackingException(String message) {
        super(message);
    }
}
