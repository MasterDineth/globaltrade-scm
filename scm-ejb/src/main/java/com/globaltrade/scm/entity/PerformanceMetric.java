package com.globaltrade.scm.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "performance_metric", indexes = {
        @Index(name = "idx_metric_vendor", columnList = "vendor_id"),
        @Index(name = "idx_metric_type", columnList = "metric_type")
})
public class PerformanceMetric implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "performance_metric_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @ManyToOne(optional = false)
    @JoinColumn(name = "metric_type_id")
    private MetricTypeEntity metricType;

    @Column(name = "value", nullable = false)
    private double value;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public void setVendor(Vendor vendor) {
        this.vendor = vendor;
    }

    public MetricTypeEntity getMetricType() {
        return metricType;
    }

    public void setMetricType(MetricTypeEntity metricType) {
        this.metricType = metricType;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }
}
