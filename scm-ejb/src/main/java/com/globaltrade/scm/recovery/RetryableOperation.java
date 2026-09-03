package com.globaltrade.scm.recovery;

import com.globaltrade.scm.exception.SupplyChainException;

/**
 * A unit of work that {@link ExceptionRecoveryManager#executeWithRetry}
 * can attempt multiple times. Declared to throw {@link SupplyChainException}
 * (the application-exception base type) rather than a wider {@code Exception}
 * deliberately: retry-with-backoff is a strategy for transient *business*
 * failures talking to an external system (a carrier API timing out, a
 * customs gateway returning a 503), never for unchecked system exceptions,
 * which should propagate immediately and roll back the caller's
 * transaction rather than being masked behind a retry loop.
 */
@FunctionalInterface
public interface RetryableOperation<T> {
    T attempt() throws SupplyChainException;
}
