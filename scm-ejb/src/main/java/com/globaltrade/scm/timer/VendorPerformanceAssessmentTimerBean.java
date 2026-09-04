package com.globaltrade.scm.timer;
import com.globaltrade.scm.entity.PerformanceMetric;
import com.globaltrade.scm.entity.MetricTypeEntity;
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
        MetricTypeEntity mt = em.createQuery("SELECT m FROM MetricTypeEntity m WHERE m.name = 'ON_TIME_DELIVERY_RATE'", MetricTypeEntity.class)
                .getSingleResult();
        PerformanceMetric metric = new PerformanceMetric();
        metric.setVendor(vendor);
        metric.setMetricType(mt);
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
