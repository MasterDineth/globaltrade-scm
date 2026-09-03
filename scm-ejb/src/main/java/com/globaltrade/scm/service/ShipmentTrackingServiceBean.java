package com.globaltrade.scm.service;

import com.globaltrade.scm.common.dto.ShipmentTrackingResult;
import com.globaltrade.scm.common.enums.ShipmentStatus;
import com.globaltrade.scm.entity.Carrier;
import com.globaltrade.scm.entity.Shipment;
import com.globaltrade.scm.entity.Vendor;
import com.globaltrade.scm.exception.ShipmentTrackingException;
import com.globaltrade.scm.exception.SupplyChainSystemException;
import com.globaltrade.scm.interceptor.PerformanceMonitoringInterceptor;
import com.globaltrade.scm.service.local.ShipmentTrackingServiceLocal;
import com.globaltrade.scm.service.remote.ShipmentTrackingServiceRemote;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.interceptor.Interceptors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Primary shipment-tracking business service. Implements both
 * {@link ShipmentTrackingServiceLocal} and {@link ShipmentTrackingServiceRemote}
 * -- see docs/CRITICAL_ANALYSIS.md, "Remote vs. local interface selection",
 * for why this bean specifically (unlike, say,
 * {@code InventoryManagementServiceBean}) warrants a remote-facing surface.
 *
 * <p>Class-level transaction attribute is left at the EJB-spec default
 * ({@code REQUIRED}); individual methods override it where the default is
 * wrong for that specific operation. {@link #trackShipment} uses
 * {@code NOT_SUPPORTED} (a pure read with nothing to protect
 * transactionally, and no reason to hold a JTA transaction -- and the
 * connection pool resources behind it -- open for the duration of a
 * lookup). {@link #recordCarrierStatusUpdate} uses {@code REQUIRES_NEW}
 * (an independently-committing inbound update, called both interactively
 * and by {@code ShipmentStatusUpdateTimerBean}'s 15-minute poll, where one
 * shipment's failure must not affect any other shipment processed in the
 * same timer run). See docs/CRITICAL_ANALYSIS.md, "Transaction attribute
 * selection for different logistics scenarios", for the full comparison.</p>
 */
@Stateless
public class ShipmentTrackingServiceBean implements ShipmentTrackingServiceLocal, ShipmentTrackingServiceRemote {

    @PersistenceContext(unitName = "scmPU")
    private EntityManager em;

    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @RolesAllowed({"ADMIN", "LOGISTICS_COORDINATOR", "WAREHOUSE_MANAGER", "CUSTOMS_AGENT",
            "VENDOR_REPRESENTATIVE", "CUSTOMER"})
    @Interceptors(PerformanceMonitoringInterceptor.class)
    public ShipmentTrackingResult trackShipment(String trackingNumber) throws ShipmentTrackingException {
        Shipment shipment = findByTrackingNumberOrThrow(trackingNumber);
        return toTrackingResult(shipment);
    }

    @Override
    @RolesAllowed({"ADMIN", "LOGISTICS_COORDINATOR"})
    public Shipment registerShipment(String trackingNumber, Long vendorId, Long carrierId,
                                      String originCountry, String destinationCountry,
                                      Double weightKg, LocalDateTime estimatedDelivery)
            throws ShipmentTrackingException {
        Vendor vendor = em.find(Vendor.class, vendorId);
        if (vendor == null) {
            throw new ShipmentTrackingException("Unknown vendor id: " + vendorId);
        }
        Carrier carrier = null;
        if (carrierId != null) {
            carrier = em.find(Carrier.class, carrierId);
            if (carrier == null) {
                throw new ShipmentTrackingException("Unknown carrier id: " + carrierId);
            }
        }

        Shipment shipment = new Shipment();
        shipment.setTrackingNumber(trackingNumber);
        shipment.setVendor(vendor);
        shipment.setCarrier(carrier);
        shipment.setOriginCountry(originCountry);
        shipment.setDestinationCountry(destinationCountry);
        shipment.setWeightKg(weightKg);
        shipment.setEstimatedDelivery(estimatedDelivery);
        shipment.setStatus(ShipmentStatus.CREATED);
        em.persist(shipment);
        return shipment;
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    @RolesAllowed({"ADMIN", "LOGISTICS_COORDINATOR"})
    public void recordCarrierStatusUpdate(String trackingNumber, ShipmentStatus newStatus,
                                           LocalDateTime actualDelivery) throws ShipmentTrackingException {
        Shipment shipment = findByTrackingNumberOrThrow(trackingNumber);
        shipment.setStatus(newStatus);
        if (actualDelivery != null) {
            shipment.setActualDelivery(actualDelivery);
        }
        mergeWithOptimisticLockHandling(shipment);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @RolesAllowed({"ADMIN", "LOGISTICS_COORDINATOR", "WAREHOUSE_MANAGER"})
    public List<Shipment> findActiveShipments() {
        // LEFT JOIN FETCH, not a plain WHERE-filtered SELECT: vendor and
        // carrier are both FetchType.LAZY on Shipment, and this method runs
        // NOT_SUPPORTED (no JTA transaction, so no persistence context is
        // guaranteed to still be open once control returns to the caller).
        // Without the eager fetch here, a caller that reads
        // shipment.getVendor()/getCarrier() after this method returns --
        // exactly what DTO-mapping code in this class and in the REST layer
        // does -- would hit a lazy-initialization failure instead of a
        // fresh query, because there is no live persistence context left to
        // satisfy it. See docs/CRITICAL_ANALYSIS.md, "EJB component
        // lifecycle management in logistics contexts."
        TypedQuery<Shipment> query = em.createQuery(
                "SELECT s FROM Shipment s LEFT JOIN FETCH s.vendor LEFT JOIN FETCH s.carrier "
                        + "WHERE s.status NOT IN :terminal", Shipment.class);
        query.setParameter("terminal", List.of(ShipmentStatus.DELIVERED, ShipmentStatus.EXCEPTION));
        return query.getResultList();
    }

    @Override
    @RolesAllowed({"ADMIN", "LOGISTICS_COORDINATOR"})
    public void cancelShipment(String trackingNumber, String reason) throws ShipmentTrackingException {
        Shipment shipment = findByTrackingNumberOrThrow(trackingNumber);
        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            throw new ShipmentTrackingException(
                    "Cannot cancel shipment " + trackingNumber + ": already delivered.");
        }
        // ShipmentStatus has no dedicated CANCELLED state; EXCEPTION is the
        // established terminal "stopped short of delivery" state used
        // consistently across this module (see ShipmentStatus javadoc).
        shipment.setStatus(ShipmentStatus.EXCEPTION);
        mergeWithOptimisticLockHandling(shipment);
    }

    /**
     * {@code Shipment.version} exists specifically so that this 15-minute
     * carrier-polling timer's writes and an interactive user's writes to
     * the same shipment can race safely -- see that field's own javadoc.
     * This helper is the "service layer" it promises will turn the loser's
     * {@link OptimisticLockException} into a {@code SupplyChainSystemException}.
     *
     * <p>Deliberately does NOT retry the merge automatically (contrast with
     * {@code ExceptionRecoveryManager}'s retry loop for carrier-API calls):
     * an optimistic-lock conflict means the in-memory {@code shipment}
     * instance was built from a row version that another writer has since
     * superseded, so blindly re-submitting the exact same merge would just
     * fail identically against the new version. The only correct recovery
     * is to re-read the current row and recompute the intended change
     * against it -- a decision that belongs to the original caller (who
     * knows what change it actually wanted), not to this method replaying
     * stale data. {@code SupplyChainSystemException} is unchecked
     * specifically so the container marks the transaction for rollback
     * automatically, and its message is written to read as "safe to retry
     * the whole operation from scratch," which is what
     * {@code SupplyChainSystemExceptionMapper} at the REST boundary
     * surfaces to the client.</p>
     */
    private void mergeWithOptimisticLockHandling(Shipment shipment) {
        try {
            em.merge(shipment);
        } catch (OptimisticLockException conflict) {
            throw new SupplyChainSystemException(
                    "Shipment " + shipment.getTrackingNumber() + " was updated concurrently by another "
                            + "operation; re-read its current state and retry.", conflict);
        }
    }

    /**
     * Shared by every method above, including {@link #trackShipment}, which
     * runs {@code NOT_SUPPORTED} and maps the result to
     * {@link ShipmentTrackingResult} -- reading {@code vendor}/{@code carrier}
     * -- within that same (transaction-less) method call. The
     * {@code LEFT JOIN FETCH}s make that safe (see {@link #findActiveShipments()}
     * for the fuller explanation) at a small, fixed extra cost for the
     * other two callers ({@link #recordCarrierStatusUpdate},
     * {@link #cancelShipment}), which do not need vendor/carrier but run
     * under a real transaction either way and are not read-heavy hot paths.
     */
    private Shipment findByTrackingNumberOrThrow(String trackingNumber) throws ShipmentTrackingException {
        try {
            TypedQuery<Shipment> query = em.createQuery(
                    "SELECT s FROM Shipment s LEFT JOIN FETCH s.vendor LEFT JOIN FETCH s.carrier "
                            + "WHERE s.trackingNumber = :trackingNumber", Shipment.class);
            query.setParameter("trackingNumber", trackingNumber);
            return query.getSingleResult();
        } catch (NoResultException e) {
            throw new ShipmentTrackingException("No shipment found for tracking number: " + trackingNumber);
        }
    }

    private ShipmentTrackingResult toTrackingResult(Shipment shipment) {
        return new ShipmentTrackingResult(
                shipment.getTrackingNumber(),
                shipment.getStatus(),
                shipment.getOriginCountry(),
                shipment.getDestinationCountry(),
                shipment.getEstimatedDelivery(),
                shipment.getActualDelivery(),
                shipment.getCarrier() != null ? shipment.getCarrier().getName() : null,
                shipment.getVendor() != null ? shipment.getVendor().getName() : null);
    }
}
