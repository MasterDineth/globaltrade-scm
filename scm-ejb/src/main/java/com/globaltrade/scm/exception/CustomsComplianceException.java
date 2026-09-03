package com.globaltrade.scm.exception;

import jakarta.ejb.ApplicationException;

/**
 * Thrown when a customs filing fails a regulatory compliance check
 * (missing declaration data, expired import license, trade-agreement
 * mismatch). {@code rollback = true}: a non-compliant document must never
 * be left in a "submitted" state, since that would misrepresent the
 * shipment's regulatory status to downstream systems and auditors.
 */
@ApplicationException(rollback = true)
public class CustomsComplianceException extends SupplyChainException {

    private static final long serialVersionUID = 1L;

    public CustomsComplianceException(String message) {
        super(message);
    }
}
