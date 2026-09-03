package com.globaltrade.scm.timer;

import com.globaltrade.scm.common.enums.ShipmentStatus;
import com.globaltrade.scm.entity.Shipment;
import com.globaltrade.scm.exception.CarrierSystemUnavailableException;
import com.globaltrade.scm.exception.SupplyChainException;
import com.globaltrade.scm.interceptor.PerformanceMonitoringInterceptor;
import com.globaltrade.scm.recovery.ExceptionRecoveryManager;
import com.globaltrade.scm.service.local.ShipmentTrackingServiceLocal;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.EJB;
import jakarta.ejb.Schedule;
import jakarta.ejb.Stateless;
import jakarta.ejb.Timer;
import jakarta.interceptor.Interceptors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DECLARATIVE (automatic) timer example. The schedule is fixed and known
 * at design time -- carriers are polled every 15 minutes, all day, every
 * day -- so an annotation-driven automatic timer is the right tool: the
 * container creates and persists exactly one timer per {@code @Schedule}
 * at deployment, with zero bean code needed to manage its lifecycle.
 * Contrast with {@link VendorPerformanceAssessmentTimerBean} and
 * {@link CustomsDeadlineTimerBean}, where the schedule itself is business
 * data unknown until runtime and therefore MUST be created programmatically.
 *
 * <p>Per-shipment status changes are delegated to
 * {@code ShipmentTrackingServiceLocal.recordCarrierStatusUpdate}
 * ({@code REQUIRES_NEW}) rather than merged directly through this bean's
 * own {@code EntityManager}, so that (a) the update business rule lives in
 * exactly one place regardless of whether it is triggered by this timer or
 * by an interactive/remote caller, and (b) each shipment's update commits
 * independently -- a persistence failure on shipment N cannot undo updates
 * already committed for shipments {@code 1..N-1} in the same run. Each
 * carrier poll is also wrapped in {@code ExceptionRecoveryManager}'s
 * bounded retry, so a single transient blip gets a couple of fast in-run
 * retries before falling back to "wait for the next 15-minute cycle."
 * {@code @RunAs("ADMIN")} is required because this timer callback has no
 * interactively-authenticated caller identity to present when it invokes
 * {@code recordCarrierStatusUpdate}, which is {@code @RolesAllowed}-protected
 * -- see docs/CRITICAL_ANALYSIS.md, "Authentication mechanisms for
 * different user types and emergency logistics scenarios".</p>
 */
@Stateless
@RunAs("ADMIN")
public class ShipmentStatusUpdateTimerBean {

    private static final Logger LOGGER = Logger.getLogger(ShipmentStatusUpdateTimerBean.class.getName());

    @PersistenceContext(unitName = "scmPU")
    private EntityManager em;

    @EJB
    private ShipmentTrackingServiceLocal shipmentTrackingService;

    @EJB
    private ExceptionRecoveryManager exceptionRecoveryManager;

    @Schedule(minute = "*/15", hour = "*", persistent = true, info = "shipment-status-poll")
    @Interceptors(PerformanceMonitoringInterceptor.class)
    public void pollCarrierSystemsAndUpdateStatuses(Timer timer) {
        List<Shipment> active = findActiveShipments();
        int updated = 0;
        int failed = 0;
        for (Shipment shipment : active) {
            try {
                ShipmentStatus latest = exceptionRecoveryManager.executeWithRetry(
                        () -> queryCarrierStatus(shipment), "CARRIER_STATUS_POLL", shipment.getTrackingNumber());
                if (latest != shipment.getStatus()) {
                    LocalDateTime actualDelivery = latest == ShipmentStatus.DELIVERED ? LocalDateTime.now() : null;
                    shipmentTrackingService.recordCarrierStatusUpdate(
                            shipment.getTrackingNumber(), latest, actualDelivery);
                }
                updated++;
            } catch (SupplyChainException e) {
                // A single unreachable/inconsistent carrier must not abort
                // the whole batch (see @ApplicationException(rollback=false)
                // on CarrierSystemUnavailableException) -- ExceptionRecoveryManager
                // has already retried this shipment and recorded a
                // FailedOperation dead-letter row above; it is simply
                // retried again on the next 15-minute cycle.
                failed++;
                LOGGER.log(Level.WARNING, "Carrier status update failed for shipment {0}: {1}",
                        new Object[]{shipment.getTrackingNumber(), e.getMessage()});
            }
        }
        final int updatedCount = updated;
        final int failedCount = failed;
        LOGGER.info(() -> String.format("Timer[%s] run complete: updated=%d failed=%d",
                timer.getInfo(), updatedCount, failedCount));
    }

    private List<Shipment> findActiveShipments() {
        // LEFT JOIN FETCH s.carrier: queryCarrierStatus() below reads
        // shipment.getCarrier() for every row in this list. This method
        // runs inside the timer's own REQUIRED transaction, so a plain
        // (non-fetch-joined) query would still be *correct* -- the lazy
        // proxy would resolve fine against the still-open persistence
        // context -- but it would cost one extra SELECT per shipment (a
        // classic N+1). Fetch-joining it here turns that into a single
        // query for the whole batch. vendor is intentionally NOT
        // fetch-joined: nothing in this timer reads it.
        TypedQuery<Shipment> query = em.createQuery(
                "SELECT s FROM Shipment s LEFT JOIN FETCH s.carrier WHERE s.status NOT IN :terminal",
                Shipment.class);
        query.setParameter("terminal", List.of(ShipmentStatus.DELIVERED, ShipmentStatus.EXCEPTION));
        return query.getResultList();
    }

    /**
     * Placeholder for the outbound call to a carrier's tracking API / EDI
     * feed. In production this is a REST/SOAP client wrapped with a
     * circuit breaker; here it simulates the exact failure mode the
     * exception-handling strategy above is built around.
     */
    private ShipmentStatus queryCarrierStatus(Shipment shipment) throws CarrierSystemUnavailableException {
        if (shipment.getCarrier() == null || !shipment.getCarrier().isActive()) {
            throw new CarrierSystemUnavailableException(
                    "Carrier system offline for shipment " + shipment.getTrackingNumber());
        }
        return shipment.getStatus(); // no-op simulation; a real integration returns the polled status
    }
}
