package com.globaltrade.scm.timer;

import com.globaltrade.scm.entity.PerformanceMetric;
import com.globaltrade.scm.entity.Vendor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.ScheduleExpression;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.Timeout;
import jakarta.ejb.Timer;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

/**
 * PROGRAMMATIC timer example, in deliberate contrast to the {@code @Schedule}
 * beans in this package. Vendor assessment day is a piece of *business
 * configuration* (contractually agreed per vendor cohort, changeable by
 * ops without a redeploy) rather than a fixed fact known at compile time
 * -- exactly the situation where the EJB Timer Service API
 * ({@code timerService.createCalendarTimer}) is the correct tool, because
 * an annotation's schedule expression cannot be parameterized at runtime.
 *
 * <p>Placed on a {@code @Singleton} with {@code @Startup} and the timer
 * created from {@code @PostConstruct} -- NOT on a {@code @Stateless} bean.
 * A {@code @Stateless} bean's {@code @PostConstruct} runs once PER POOLED
 * INSTANCE, so calling {@code createCalendarTimer} there would register a
 * duplicate timer every time the container grows the pool. A singleton's
 * {@code @PostConstruct} runs exactly once per application lifecycle,
 * which is the guarantee a "register this recurring job on startup"
 * pattern actually needs.</p>
 *
 * <p>The idempotency check in {@link #scheduleAssessmentTimer()} additionally
 * guards against duplicate registration across redeploys / container
 * restarts where persistent timers from a prior deployment may still be
 * active -- part of the "timer persistence and reliability in globally
 * distributed logistics environments" analysis (see docs/CRITICAL_ANALYSIS.md).</p>
 */
@Singleton
@Startup
public class VendorPerformanceAssessmentTimerBean {

    private static final Logger LOGGER = Logger.getLogger(VendorPerformanceAssessmentTimerBean.class.getName());
    private static final String TIMER_INFO = "vendor-performance-assessment";

    @Resource
    private TimerService timerService;

    @PersistenceContext(unitName = "scmPU")
    private EntityManager em;

    @PostConstruct
    public void scheduleAssessmentTimer() {
        if (timerAlreadyRegistered()) {
            LOGGER.info("Vendor performance assessment timer already registered; skipping creation.");
            return;
        }

        // Stand-in for a manageable configuration source (JNDI env-entry,
        // config-admin console, or a DB-backed settings table). The point
        // being illustrated is that this value is NOT known until runtime,
        // which is precisely why it cannot be expressed as a compile-time
        // @Schedule annotation.
        String configuredDay = System.getProperty("scm.vendorAssessment.dayOfMonth", "1");

        ScheduleExpression schedule = new ScheduleExpression()
                .dayOfMonth(configuredDay)
                .hour("3")
                .minute("0");

        TimerConfig config = new TimerConfig(TIMER_INFO, true);
        timerService.createCalendarTimer(schedule, config);
        LOGGER.info(() -> "Registered vendor performance assessment timer for day-of-month=" + configuredDay);
    }

    private boolean timerAlreadyRegistered() {
        Collection<Timer> timers = timerService.getTimers();
        return timers.stream().anyMatch(t -> TIMER_INFO.equals(t.getInfo()));
    }

    @Timeout
    public void runAssessment(Timer timer) {
        if (!TIMER_INFO.equals(timer.getInfo())) {
            return;
        }
        List<Vendor> vendors = findActiveVendors();
        for (Vendor vendor : vendors) {
            assessVendor(vendor);
        }
        LOGGER.info(() -> "Vendor performance assessment complete for " + vendors.size() + " vendor(s)");
    }

    private List<Vendor> findActiveVendors() {
        TypedQuery<Vendor> query = em.createQuery(
                "SELECT v FROM Vendor v WHERE v.active = true", Vendor.class);
        return query.getResultList();
    }

    private void assessVendor(Vendor vendor) {
        Long onTimeCount = countOnTimeDeliveries(vendor);
        Long totalCount = countCompletedShipments(vendor);
        double rate = (totalCount == null || totalCount == 0)
                ? 0.0
                : (onTimeCount == null ? 0.0 : onTimeCount) * 100.0 / totalCount;

        vendor.setPerformanceScore(rate);

        PerformanceMetric metric = new PerformanceMetric();
        metric.setVendor(vendor);
        metric.setMetricType("ON_TIME_DELIVERY_RATE");
        metric.setValue(rate);
        metric.setRecordedAt(LocalDateTime.now());
        em.persist(metric);
    }

    private Long countOnTimeDeliveries(Vendor vendor) {
        TypedQuery<Long> query = em.createQuery(
                "SELECT COUNT(s) FROM Shipment s WHERE s.vendor = :vendor "
                        + "AND s.actualDelivery IS NOT NULL AND s.estimatedDelivery IS NOT NULL "
                        + "AND s.actualDelivery <= s.estimatedDelivery", Long.class);
        query.setParameter("vendor", vendor);
        return query.getSingleResult();
    }

    private Long countCompletedShipments(Vendor vendor) {
        TypedQuery<Long> query = em.createQuery(
                "SELECT COUNT(s) FROM Shipment s WHERE s.vendor = :vendor AND s.actualDelivery IS NOT NULL", Long.class);
        query.setParameter("vendor", vendor);
        return query.getSingleResult();
    }
}
