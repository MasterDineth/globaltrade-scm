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
        TypedQuery<Shipment> query = em.createQuery(
                "SELECT s FROM Shipment s LEFT JOIN FETCH s.carrier WHERE s.status NOT IN :terminal",
                Shipment.class);
        query.setParameter("terminal", List.of(ShipmentStatus.DELIVERED, ShipmentStatus.EXCEPTION));
        return query.getResultList();
    }
    private ShipmentStatus queryCarrierStatus(Shipment shipment) throws CarrierSystemUnavailableException {
        if (shipment.getCarrier() == null || !shipment.getCarrier().isActive()) {
            throw new CarrierSystemUnavailableException(
                    "Carrier system offline for shipment " + shipment.getTrackingNumber());
        }
        return shipment.getStatus(); 
    }
}
