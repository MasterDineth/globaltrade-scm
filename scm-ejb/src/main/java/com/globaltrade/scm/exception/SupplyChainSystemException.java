package com.globaltrade.scm.exception;

/**
 * Root unchecked exception for unexpected, unrecoverable-by-the-caller
 * failures (data corruption, programming errors, resources that should
 * never be unavailable). Per EJB semantics, an unchecked exception thrown
 * out of a business method automatically marks the current transaction
 * for rollback and, for remote clients, is unwrapped/rewrapped by the
 * container as {@code jakarta.ejb.EJBException}. Application code should
 * not catch these except at a top-level boundary (e.g. a JAX-RS
 * {@code ExceptionMapper}) purely to translate them into a generic error
 * response -- never to "handle" and continue, which would defeat the
 * point of using a system exception in the first place.
 */
public class SupplyChainSystemException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SupplyChainSystemException(String message) {
        super(message);
    }

    public SupplyChainSystemException(String message, Throwable cause) {
        super(message, cause);
    }
}
