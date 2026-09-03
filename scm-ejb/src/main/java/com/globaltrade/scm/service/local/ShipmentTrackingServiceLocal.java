package com.globaltrade.scm.service.local;

import com.globaltrade.scm.common.dto.ShipmentTrackingResult;
import com.globaltrade.scm.common.enums.ShipmentStatus;
import com.globaltrade.scm.entity.Shipment;
import com.globaltrade.scm.exception.ShipmentTrackingException;
import jakarta.ejb.Local;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full internal API surface for shipment tracking, available only to
 * co-deployed components within this EAR. Contrast with
 * {@link com.globaltrade.scm.service.remote.ShipmentTrackingServiceRemote},
 * which exposes only the subset of operations genuinely needed by
 * out-of-process carrier and vendor/customer-portal integrations -- see
 * docs/CRITICAL_ANALYSIS.md, "Remote vs. local interface selection", for
 * the full rationale behind the split.
 */
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
