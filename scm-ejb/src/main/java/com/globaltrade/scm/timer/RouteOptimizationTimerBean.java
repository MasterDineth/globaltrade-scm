package com.globaltrade.scm.timer;

import com.globaltrade.scm.common.enums.ShipmentStatus;
import com.globaltrade.scm.entity.Shipment;
import com.globaltrade.scm.interceptor.PerformanceMonitoringInterceptor;
import jakarta.ejb.Schedule;
import jakarta.ejb.Stateless;
import jakarta.interceptor.Interceptors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.logging.Logger;

/**
 * DECLARATIVE nightly batch timer (02:00, Monday-Friday) that recalculates
 * optimized routes for shipments not yet picked up. A well-known, static
 * business schedule -- the textbook case where a simple {@code @Schedule}
 * annotation is preferable to programmatic timer creation: no runtime
 * decision is involved in *when* it should run, only in what it does once
 * it fires.
 */
@Stateless
public class RouteOptimizationTimerBean {

    private static final Logger LOGGER = Logger.getLogger(RouteOptimizationTimerBean.class.getName());

    @PersistenceContext(unitName = "scmPU")
    private EntityManager em;

    @Schedule(dayOfWeek = "Mon-Fri", hour = "2", minute = "0", persistent = true, info = "route-optimization-nightly")
    @Interceptors(PerformanceMonitoringInterceptor.class)
    public void recalculateRoutes() {
        List<Shipment> pending = findShipmentsPendingPickup();
        for (Shipment shipment : pending) {
            optimizeRoute(shipment);
        }
        LOGGER.info(() -> "Route optimization run complete for " + pending.size() + " shipment(s)");
    }

    private List<Shipment> findShipmentsPendingPickup() {
        TypedQuery<Shipment> query = em.createQuery(
                "SELECT s FROM Shipment s WHERE s.status = :status", Shipment.class);
        query.setParameter("status", ShipmentStatus.CREATED);
        return query.getResultList();
    }

    private void optimizeRoute(Shipment shipment) {
        // Placeholder for a real routing/optimization engine call (distance
        // matrix, carrier capacity, customs-checkpoint transit times).
        LOGGER.fine(() -> "Recalculated route for shipment " + shipment.getTrackingNumber());
    }
}
