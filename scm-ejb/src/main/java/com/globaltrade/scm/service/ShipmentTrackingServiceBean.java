package com.globaltrade.scm.service;
import com.globaltrade.scm.common.dto.ShipmentTrackingResult;
import com.globaltrade.scm.common.enums.ShipmentStatus;
import com.globaltrade.scm.entity.Carrier;
import com.globaltrade.scm.entity.Country;
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
        Country origin = em.createQuery("SELECT c FROM Country c WHERE c.code = :code", Country.class)
                .setParameter("code", originCountry).getSingleResult();
        Country dest = em.createQuery("SELECT c FROM Country c WHERE c.code = :code", Country.class)
                .setParameter("code", destinationCountry).getSingleResult();
        Shipment shipment = new Shipment();
        shipment.setTrackingNumber(trackingNumber);
        shipment.setVendor(vendor);
        shipment.setCarrier(carrier);
        shipment.setOriginCountry(origin);
        shipment.setDestinationCountry(dest);
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
        shipment.setStatus(ShipmentStatus.EXCEPTION);
        mergeWithOptimisticLockHandling(shipment);
    }
    private void mergeWithOptimisticLockHandling(Shipment shipment) {
        try {
            em.merge(shipment);
        } catch (OptimisticLockException conflict) {
            throw new SupplyChainSystemException(
                    "Shipment " + shipment.getTrackingNumber() + " was updated concurrently by another "
                            + "operation; re-read its current state and retry.", conflict);
        }
    }
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
                shipment.getOriginCountry().getCode(),
                shipment.getDestinationCountry().getCode(),
                shipment.getEstimatedDelivery(),
                shipment.getActualDelivery(),
                shipment.getCarrier() != null ? shipment.getCarrier().getName() : null,
                shipment.getVendor() != null ? shipment.getVendor().getName() : null);
    }
}
