package com.globaltrade.scm.common.dto;
import java.io.Serializable;
import java.time.LocalDateTime;
public class VendorPerformanceSummary implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long vendorId;
    private String vendorName;
    private double onTimeDeliveryRate;
    private double averageScore;
    private long shipmentsEvaluated;
    private LocalDateTime assessedAt;
    public VendorPerformanceSummary() {
    }
    public VendorPerformanceSummary(Long vendorId, String vendorName, double onTimeDeliveryRate,
                                     double averageScore, long shipmentsEvaluated, LocalDateTime assessedAt) {
        this.vendorId = vendorId;
        this.vendorName = vendorName;
        this.onTimeDeliveryRate = onTimeDeliveryRate;
        this.averageScore = averageScore;
        this.shipmentsEvaluated = shipmentsEvaluated;
        this.assessedAt = assessedAt;
    }
    public Long getVendorId() {
        return vendorId;
    }
    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
    }
    public String getVendorName() {
        return vendorName;
    }
    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }
    public double getOnTimeDeliveryRate() {
        return onTimeDeliveryRate;
    }
    public void setOnTimeDeliveryRate(double onTimeDeliveryRate) {
        this.onTimeDeliveryRate = onTimeDeliveryRate;
    }
    public double getAverageScore() {
        return averageScore;
    }
    public void setAverageScore(double averageScore) {
        this.averageScore = averageScore;
    }
    public long getShipmentsEvaluated() {
        return shipmentsEvaluated;
    }
    public void setShipmentsEvaluated(long shipmentsEvaluated) {
        this.shipmentsEvaluated = shipmentsEvaluated;
    }
    public LocalDateTime getAssessedAt() {
        return assessedAt;
    }
    public void setAssessedAt(LocalDateTime assessedAt) {
        this.assessedAt = assessedAt;
    }
}
