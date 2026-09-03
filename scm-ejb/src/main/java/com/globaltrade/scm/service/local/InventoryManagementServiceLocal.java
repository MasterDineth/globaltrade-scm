package com.globaltrade.scm.service.local;

import com.globaltrade.scm.entity.InventoryItem;
import com.globaltrade.scm.exception.InsufficientInventoryException;
import jakarta.ejb.Local;

import java.util.List;

/**
 * Local-only by design: nothing outside this EAR mutates warehouse stock
 * directly. A future warehouse-management-system integration would speak
 * to a dedicated adapter/gateway component rather than this service
 * directly, so that inventory business rules (reservation accounting,
 * replenishment provenance) can never be bypassed by an external caller
 * invoking a remote interface. See docs/CRITICAL_ANALYSIS.md, "Remote vs.
 * local interface selection", for the paired discussion of where remote
 * access IS warranted (shipment tracking, vendor performance).
 */
@Local
public interface InventoryManagementServiceLocal {

    InventoryItem getItem(String sku);

    boolean checkAvailability(String sku, int quantity);

    void reserveStock(String sku, int quantity) throws InsufficientInventoryException;

    void replenishStock(String sku, int quantityToAdd, String sourceReference);

    List<InventoryItem> findBelowReorderThreshold();
}
