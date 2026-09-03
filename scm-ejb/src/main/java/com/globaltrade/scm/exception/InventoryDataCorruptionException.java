package com.globaltrade.scm.exception;

/**
 * Unchecked system exception raised when inventory data is found in a
 * state the domain model says should be impossible (e.g. negative
 * quantity-on-hand surviving a constraint that should have prevented it,
 * or a reconciliation job finding the warehouse feed and the SCM database
 * irreconcilably out of sync). This is deliberately NOT an
 * {@code @ApplicationException}: callers are not expected to catch and
 * "handle" data corruption as a business outcome -- the transaction must
 * roll back automatically, the container must log it as an error, and it
 * must surface to on-call operations rather than being swallowed by a
 * business-logic catch block.
 */
public class InventoryDataCorruptionException extends SupplyChainSystemException {

    private static final long serialVersionUID = 1L;

    public InventoryDataCorruptionException(String message) {
        super(message);
    }
}
