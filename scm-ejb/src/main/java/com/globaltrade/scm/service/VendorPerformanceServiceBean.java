package com.globaltrade.scm.service;

import com.globaltrade.scm.common.dto.VendorPerformanceSummary;
import com.globaltrade.scm.entity.PerformanceMetric;
import com.globaltrade.scm.entity.MetricTypeEntity;
import com.globaltrade.scm.entity.Vendor;
import com.globaltrade.scm.entity.Country;
import com.globaltrade.scm.exception.VendorDataValidationException;
import com.globaltrade.scm.interceptor.VendorDataValidationInterceptor;
import com.globaltrade.scm.service.local.VendorPerformanceServiceLocal;
import com.globaltrade.scm.service.remote.VendorPerformanceServiceRemote;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.AsyncResult;
import jakarta.ejb.Asynchronous;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.interceptor.Interceptors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Class-level {@link VendorDataValidationInterceptor} binding (see that
 * interceptor's javadoc, which names this bean explicitly): every public
 * method here accepts either a {@code Vendor} entity or a vendor id, so
 * validating vendor-shaped input uniformly before any business logic runs
 * is a whole-bean policy, not a per-method opt-in that a future method
 * could accidentally omit.
 */
@Stateless
@Interceptors(VendorDataValidationInterceptor.class)
public class VendorPerformanceServiceBean implements VendorPerformanceServiceLocal, VendorPerformanceServiceRemote {

    @PersistenceContext(unitName = "scmPU")
    private EntityManager em;

    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @RolesAllowed({"ADMIN", "LOGISTICS_COORDINATOR", "VENDOR_REPRESENTATIVE"})
    public VendorPerformanceSummary getVendorPerformanceSummary(Long vendorId) throws VendorDataValidationException {
        Vendor vendor = requireVendor(vendorId);
        return buildSummary(vendor);
    }

    @Override
    @RolesAllowed({"ADMIN", "LOGISTICS_COORDINATOR"})
    public Vendor registerVendor(Vendor vendor) throws VendorDataValidationException {
        // VendorDataValidationInterceptor has already validated `vendor`
        // (it matches parameters of type Vendor) before this method body
        // runs, so no redundant field-level checks are needed here.
        Country c = em.createQuery("SELECT c FROM Country c WHERE c.code = :code", Country.class)
                .setParameter("code", vendor.getCountry().getCode())
                .getSingleResult();
        vendor.setCountry(c);
        em.persist(vendor);
        return vendor;
    }

    @Override
    @RolesAllowed({"ADMIN", "VENDOR_REPRESENTATIVE"})
    public void updateVendorProfile(Long vendorId, String contactEmail) throws VendorDataValidationException {
        Vendor vendor = requireVendor(vendorId);
        if (contactEmail == null || !contactEmail.contains("@")) {
            throw new VendorDataValidationException("Contact email is not a valid address: " + contactEmail);
        }
        vendor.setContactEmail(contactEmail);
        em.merge(vendor);
    }

    @Override
    @RolesAllowed({"ADMIN", "LOGISTICS_COORDINATOR"})
    public void submitVendorPerformanceReview(Long vendorId, int score, String notes)
            throws VendorDataValidationException {
        Vendor vendor = requireVendor(vendorId);
        if (score < 0 || score > 100) {
            throw new VendorDataValidationException("Review score out of range [0,100]: " + score);
        }
        MetricTypeEntity mt = em.createQuery("SELECT m FROM MetricTypeEntity m WHERE m.name = 'MANUAL_REVIEW_SCORE'", MetricTypeEntity.class)
                .getSingleResult();
        PerformanceMetric metric = new PerformanceMetric();
        metric.setVendor(vendor);
        metric.setMetricType(mt);
        metric.setValue(score);
        metric.setRecordedAt(LocalDateTime.now());
        em.persist(metric);
    }

    /**
     * {@code @Asynchronous}: the aggregation behind {@link #buildSummary}
     * scans historical {@code PerformanceMetric} and {@code Shipment} rows
     * and can legitimately take longer than a caller -- particularly a
     * remote vendor-portal client -- should have to block for.
     * {@code TransactionAttributeType.REQUIRES_NEW} is set explicitly for
     * clarity even though it is close to the container's effective default
     * here: an asynchronous method executes on a container-managed thread
     * with no caller transaction to propagate, so it always begins a new
     * transaction regardless of the caller's transactional context.
     */
    @Override
    @Asynchronous
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    @RolesAllowed({"ADMIN", "LOGISTICS_COORDINATOR", "VENDOR_REPRESENTATIVE"})
    public Future<VendorPerformanceSummary> assessVendorAsync(Long vendorId) throws VendorDataValidationException {
        Vendor vendor = requireVendor(vendorId);

        // 1. Calculate On-Time Delivery Rate
        Long onTimeCount = em.createQuery(
                "SELECT COUNT(s) FROM Shipment s WHERE s.vendor = :vendor "
                        + "AND s.actualDelivery IS NOT NULL AND s.estimatedDelivery IS NOT NULL "
                        + "AND s.actualDelivery <= s.estimatedDelivery", Long.class)
                .setParameter("vendor", vendor)
                .getSingleResult();
                
        Long totalCount = em.createQuery(
                "SELECT COUNT(s) FROM Shipment s WHERE s.vendor = :vendor AND s.actualDelivery IS NOT NULL", Long.class)
                .setParameter("vendor", vendor)
                .getSingleResult();

        double onTimeRate = (totalCount == null || totalCount == 0)
                ? 0.0
                : (onTimeCount == null ? 0.0 : onTimeCount) * 100.0 / totalCount;

        // Record the On-Time metric
        MetricTypeEntity mt = em.createQuery("SELECT m FROM MetricTypeEntity m WHERE m.name = 'ON_TIME_DELIVERY_RATE'", MetricTypeEntity.class)
                .getSingleResult();
        PerformanceMetric metric = new PerformanceMetric();
        metric.setVendor(vendor);
        metric.setMetricType(mt);
        metric.setValue(onTimeRate);
        metric.setRecordedAt(LocalDateTime.now());
        em.persist(metric);

        // 2. Calculate Manual Review Average
        Double manualAvg = em.createQuery(
                "SELECT AVG(m.value) FROM PerformanceMetric m WHERE m.vendor = :vendor AND m.metricType.name = 'MANUAL_REVIEW_SCORE'",
                Double.class)
                .setParameter("vendor", vendor)
                .getSingleResult();

        // 3. Compute Overall Score
        double overallScore = 0.0;
        if (manualAvg != null && totalCount != null && totalCount > 0) {
            overallScore = (onTimeRate + manualAvg) / 2.0;
        } else if (manualAvg != null) {
            overallScore = manualAvg;
        } else if (totalCount != null && totalCount > 0) {
            overallScore = onTimeRate;
        }
        
        vendor.setPerformanceScore(overallScore);
        em.merge(vendor);

        return new AsyncResult<>(new VendorPerformanceSummary(
                vendor.getId(), vendor.getName(), onTimeRate, overallScore, totalCount == null ? 0 : totalCount, LocalDateTime.now()));
    }

    private Vendor requireVendor(Long vendorId) throws VendorDataValidationException {
        Vendor vendor = em.find(Vendor.class, vendorId);
        if (vendor == null) {
            throw new VendorDataValidationException("Unknown vendor id: " + vendorId);
        }
        return vendor;
    }

    private VendorPerformanceSummary buildSummary(Vendor vendor) {
        TypedQuery<PerformanceMetric> metricsQuery = em.createQuery(
                "SELECT m FROM PerformanceMetric m WHERE m.vendor = :vendor "
                        + "AND m.metricType.name = 'ON_TIME_DELIVERY_RATE' ORDER BY m.recordedAt DESC",
                PerformanceMetric.class);
        metricsQuery.setParameter("vendor", vendor);
        metricsQuery.setMaxResults(1);
        List<PerformanceMetric> latest = metricsQuery.getResultList();
        double onTimeRate = latest.isEmpty() ? 0.0 : latest.get(0).getValue();

        TypedQuery<Long> shipmentCountQuery = em.createQuery(
                "SELECT COUNT(s) FROM Shipment s WHERE s.vendor = :vendor AND s.actualDelivery IS NOT NULL",
                Long.class);
        shipmentCountQuery.setParameter("vendor", vendor);
        long shipmentsEvaluated = shipmentCountQuery.getSingleResult();

        double averageScore = vendor.getPerformanceScore() != null ? vendor.getPerformanceScore() : 0.0;

        return new VendorPerformanceSummary(
                vendor.getId(), vendor.getName(), onTimeRate, averageScore, shipmentsEvaluated, LocalDateTime.now());
    }
}
