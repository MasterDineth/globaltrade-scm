package com.globaltrade.scm.exception;

import jakarta.ejb.ApplicationException;

/**
 * Thrown when an external carrier tracking/booking API cannot be reached.
 * {@code rollback = false}: this is an anticipated, transient condition --
 * the calling transaction (e.g. the 15-minute status-poll timer, which has
 * already updated N-1 other shipments in this same transaction) should be
 * allowed to commit the work it *did* complete rather than losing it, while
 * this one shipment is simply retried on the next cycle. See
 * {@code ExceptionRecoveryManager} for the bounded-retry / dead-letter
 * policy applied above this exception.
 */
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
