package com.globaltrade.scm.timer;
import com.globaltrade.scm.entity.AuditLogEntry;
import com.globaltrade.scm.entity.InventoryItem;
import jakarta.ejb.Schedule;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;
@Stateless
public class InventoryLevelMonitorTimerBean {
    private static final Logger LOGGER = Logger.getLogger(InventoryLevelMonitorTimerBean.class.getName());
    @PersistenceContext(unitName = "scmPU")
    private EntityManager em;
    @Schedule(hour = "*", minute = "0", persistent = true, info = "inventory-level-monitor")
    public void checkReorderThresholds() {
        List<InventoryItem> lowStockItems = findLowStockItems();
        for (InventoryItem item : lowStockItems) {
            raiseLowStockAlert(item);
        }
        LOGGER.info(() -> "Inventory monitor run complete: " + lowStockItems.size() + " item(s) below reorder threshold");
    }
    private List<InventoryItem> findLowStockItems() {
        TypedQuery<InventoryItem> query = em.createQuery(
                "SELECT i FROM InventoryItem i WHERE i.quantityOnHand < i.reorderThreshold", InventoryItem.class);
        return query.getResultList();
    }
    private void raiseLowStockAlert(InventoryItem item) {
        LOGGER.warning(() -> String.format("LOW STOCK ALERT: sku=%s onHand=%d threshold=%d warehouse=%s",
                item.getSku(), item.getQuantityOnHand(), item.getReorderThreshold(), item.getWarehouseLocation()));
        AuditLogEntry alert = new AuditLogEntry();
        alert.setEntityName("InventoryItem:" + item.getSku());
        alert.setEntityId(String.valueOf(item.getId()));
        alert.setAction("LOW_STOCK_ALERT");
        alert.setPerformedBy("SYSTEM_TIMER");
        alert.setTimestamp(LocalDateTime.now());
        alert.setDetails(String.format("onHand=%d threshold=%d", item.getQuantityOnHand(), item.getReorderThreshold()));
        em.persist(alert);
    }
}
