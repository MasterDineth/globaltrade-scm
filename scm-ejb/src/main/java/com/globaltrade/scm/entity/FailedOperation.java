package com.globaltrade.scm.entity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
@Entity
@Table(name = "failed_operation", indexes = {
        @Index(name = "idx_failed_op_resolved", columnList = "resolved")
})
public class FailedOperation implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "failed_operation_id")
    private Long id;
    @Column(name = "operation_type", nullable = false, length = 100)
    private String operationType;
    @Column(name = "payload", length = 4000)
    private String payload;
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;
    @Column(name = "retry_count", nullable = false)
    private int retryCount;
    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;
    @Column(name = "resolved", nullable = false)
    private boolean resolved = false;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getOperationType() {
        return operationType;
    }
    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }
    public String getPayload() {
        return payload;
    }
    public void setPayload(String payload) {
        this.payload = payload;
    }
    public String getFailureReason() {
        return failureReason;
    }
    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
    public int getRetryCount() {
        return retryCount;
    }
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
    public LocalDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }
    public void setLastAttemptAt(LocalDateTime lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }
    public boolean isResolved() {
        return resolved;
    }
    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
