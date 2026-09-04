package com.globaltrade.scm.service;
import com.globaltrade.scm.entity.InventoryItem;
import com.globaltrade.scm.exception.InsufficientInventoryException;
import com.globaltrade.scm.service.local.InventoryManagementServiceLocal;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;
@Stateless
public class InventoryManagementServiceBean implements InventoryManagementServiceLocal {
    @PersistenceContext(unitName = "scmPU")
    private EntityManager em;
    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @RolesAllowed({"ADMIN", "LOGISTICS_COORDINATOR", "WAREHOUSE_MANAGER", "VENDOR_REPRESENTATIVE"})
    public InventoryItem getItem(String sku) {
        return findBySkuOrNull(sku);
    }
    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @RolesAllowed({"ADMIN", "LOGISTICS_COORDINATOR", "WAREHOUSE_MANAGER", "VENDOR_REPRESENTATIVE"})
    public boolean checkAvailability(String sku, int quantity) {
        InventoryItem item = findBySkuOrNull(sku);
        return item != null && item.getQuantityOnHand() >= quantity;
    }
    @Override
    @RolesAllowed({"ADMIN", "LOGISTICS_COORDINATOR", "WAREHOUSE_MANAGER"})
    public void reserveStock(String sku, int quantity) throws InsufficientInventoryException {
        InventoryItem item = findBySkuOrNull(sku);
        if (item == null || item.getQuantityOnHand() < quantity) {
            int available = item == null ? 0 : item.getQuantityOnHand();
            throw new InsufficientInventoryException(sku, quantity, available);
        }
        item.setQuantityOnHand(item.getQuantityOnHand() - quantity);
        em.merge(item);
    }
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    @RolesAllowed({"ADMIN", "LOGISTICS_COORDINATOR", "WAREHOUSE_MANAGER"})
    public void replenishStock(String sku, int quantityToAdd, String sourceReference) {
        InventoryItem item = findBySkuOrNull(sku);
        if (item == null) {
            throw new IllegalArgumentException("Unknown SKU: " + sku);
        }
        item.setQuantityOnHand(item.getQuantityOnHand() + quantityToAdd);
        item.setLastRestockedAt(LocalDateTime.now());
        em.merge(item);
    }
    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @RolesAllowed({"ADMIN", "LOGISTICS_COORDINATOR", "WAREHOUSE_MANAGER"})
    public List<InventoryItem> findBelowReorderThreshold() {
        TypedQuery<InventoryItem> query = em.createQuery(
                "SELECT i FROM InventoryItem i WHERE i.quantityOnHand < i.reorderThreshold", InventoryItem.class);
        return query.getResultList();
    }
    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @RolesAllowed({"ADMIN", "LOGISTICS_COORDINATOR", "WAREHOUSE_MANAGER"})
    public List<InventoryItem> findAll() {
        return em.createQuery("SELECT i FROM InventoryItem i", InventoryItem.class).getResultList();
    }
    @Override
    @RolesAllowed({"ADMIN", "WAREHOUSE_MANAGER"})
    public InventoryItem createItem(InventoryItem item) {
        if (findBySkuOrNull(item.getSku()) != null) {
            throw new IllegalArgumentException("SKU already exists: " + item.getSku());
        }
        em.persist(item);
        return item;
    }
    @Override
    @RolesAllowed({"ADMIN", "WAREHOUSE_MANAGER"})
    public InventoryItem updateItem(String sku, InventoryItem item) {
        InventoryItem existing = findBySkuOrNull(sku);
        if (existing == null) {
            throw new IllegalArgumentException("Unknown SKU: " + sku);
        }
        existing.setDescription(item.getDescription());
        existing.setQuantityOnHand(item.getQuantityOnHand());
        existing.setReorderThreshold(item.getReorderThreshold());
        existing.setWarehouseLocation(item.getWarehouseLocation());
        em.merge(existing);
        return existing;
    }
    private InventoryItem findBySkuOrNull(String sku) {
        try {
            TypedQuery<InventoryItem> query = em.createQuery(
                    "SELECT i FROM InventoryItem i WHERE i.sku = :sku", InventoryItem.class);
            query.setParameter("sku", sku);
            return query.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}
