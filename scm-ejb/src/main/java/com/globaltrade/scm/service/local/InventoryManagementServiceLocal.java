package com.globaltrade.scm.service.local;
import com.globaltrade.scm.entity.InventoryItem;
import com.globaltrade.scm.exception.InsufficientInventoryException;
import jakarta.ejb.Local;
import java.util.List;
@Local
public interface InventoryManagementServiceLocal {
    InventoryItem getItem(String sku);
    boolean checkAvailability(String sku, int quantity);
    void reserveStock(String sku, int quantity) throws InsufficientInventoryException;
    void replenishStock(String sku, int quantityToAdd, String sourceReference);
    List<InventoryItem> findBelowReorderThreshold();
    List<InventoryItem> findAll();
    InventoryItem createItem(InventoryItem item);
    InventoryItem updateItem(String sku, InventoryItem item);
}
