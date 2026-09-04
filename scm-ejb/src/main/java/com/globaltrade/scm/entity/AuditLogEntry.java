package com.globaltrade.scm.entity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
@Entity
@Table(name = "audit_log_entry", indexes = {
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_entity", columnList = "entity_name")
})
public class AuditLogEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_log_entry_id")
    private Long id;
    @Column(name = "entity_name", nullable = false, length = 150)
    private String entityName;
    @Column(name = "entity_id", length = 64)
    private String entityId;
    @Column(name = "action", nullable = false, length = 100)
    private String action;
    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    @Column(name = "details", length = 4000)
    private String details;
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getEntityName() {
        return entityName;
    }
    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }
    public String getEntityId() {
        return entityId;
    }
    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }
    public String getAction() {
        return action;
    }
    public void setAction(String action) {
        this.action = action;
    }
    public String getPerformedBy() {
        return performedBy;
    }
    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    public String getDetails() {
        return details;
    }
    public void setDetails(String details) {
        this.details = details;
    }
}
