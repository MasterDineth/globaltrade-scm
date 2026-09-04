package com.globaltrade.scm.entity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
@Entity
@Table(name = "inventory_item", uniqueConstraints = @UniqueConstraint(name = "uq_inventory_sku", columnNames = "sku"))
public class InventoryItem implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_item_id")
    private Long id;
    @Column(name = "sku", nullable = false, length = 64)
    private String sku;
    @Column(name = "description", length = 255)
    private String description;
    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;
    @Column(name = "reorder_threshold", nullable = false)
    private int reorderThreshold;
    @Column(name = "warehouse_location", length = 100)
    private String warehouseLocation;
    @Column(name = "last_restocked_at")
    private LocalDateTime lastRestockedAt;
    @Version
    @Column(name = "version")
    private Long version;
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getSku() {
        return sku;
    }
    public void setSku(String sku) {
        this.sku = sku;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public int getQuantityOnHand() {
        return quantityOnHand;
    }
    public void setQuantityOnHand(int quantityOnHand) {
        this.quantityOnHand = quantityOnHand;
    }
    public int getReorderThreshold() {
        return reorderThreshold;
    }
    public void setReorderThreshold(int reorderThreshold) {
        this.reorderThreshold = reorderThreshold;
    }
    public String getWarehouseLocation() {
        return warehouseLocation;
    }
    public void setWarehouseLocation(String warehouseLocation) {
        this.warehouseLocation = warehouseLocation;
    }
    public LocalDateTime getLastRestockedAt() {
        return lastRestockedAt;
    }
    public void setLastRestockedAt(LocalDateTime lastRestockedAt) {
        this.lastRestockedAt = lastRestockedAt;
    }
    public Long getVersion() {
        return version;
    }
    public boolean isBelowReorderThreshold() {
        return quantityOnHand < reorderThreshold;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InventoryItem)) return false;
        InventoryItem that = (InventoryItem) o;
        return id != null && id.equals(that.id);
    }
    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
