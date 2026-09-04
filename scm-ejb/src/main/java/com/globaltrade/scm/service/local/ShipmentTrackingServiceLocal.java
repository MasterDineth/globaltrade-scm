package com.globaltrade.scm.service.local;
import com.globaltrade.scm.common.dto.ShipmentTrackingResult;
import com.globaltrade.scm.common.enums.ShipmentStatus;
import com.globaltrade.scm.entity.Shipment;
import com.globaltrade.scm.exception.ShipmentTrackingException;
import jakarta.ejb.Local;
import java.time.LocalDateTime;
import java.util.List;
@Local
public interface ShipmentTrackingServiceLocal {
    ShipmentTrackingResult trackShipment(String trackingNumber) throws ShipmentTrackingException;
    Shipment registerShipment(String trackingNumber, Long vendorId, Long carrierId,
                               String originCountry, String destinationCountry,
                               Double weightKg, LocalDateTime estimatedDelivery)
            throws ShipmentTrackingException;
    void recordCarrierStatusUpdate(String trackingNumber, ShipmentStatus newStatus,
                                    LocalDateTime actualDelivery) throws ShipmentTrackingException;
    List<Shipment> findActiveShipments();
    void cancelShipment(String trackingNumber, String reason) throws ShipmentTrackingException;
}
