package com.globaltrade.scm.exception;
import jakarta.ejb.ApplicationException;
@ApplicationException(rollback = true)
public class InsufficientInventoryException extends SupplyChainException {
    private static final long serialVersionUID = 1L;
    private final String sku;
    private final int requestedQuantity;
    private final int availableQuantity;
    public InsufficientInventoryException(String sku, int requestedQuantity, int availableQuantity) {
        super(String.format("Insufficient inventory for SKU %s: requested=%d available=%d",
                sku, requestedQuantity, availableQuantity));
        this.sku = sku;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }
    public String getSku() {
        return sku;
    }
    public int getRequestedQuantity() {
        return requestedQuantity;
    }
    public int getAvailableQuantity() {
        return availableQuantity;
    }
}
