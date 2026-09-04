package com.globaltrade.scm.recovery;
import com.globaltrade.scm.exception.SupplyChainException;
@FunctionalInterface
public interface RetryableOperation<T> {
    T attempt() throws SupplyChainException;
}
