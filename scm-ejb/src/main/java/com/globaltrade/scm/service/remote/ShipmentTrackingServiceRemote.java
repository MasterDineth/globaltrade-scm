package com.globaltrade.scm.service.remote;
import com.globaltrade.scm.common.dto.ShipmentTrackingResult;
import com.globaltrade.scm.common.enums.ShipmentStatus;
import com.globaltrade.scm.exception.ShipmentTrackingException;
import jakarta.ejb.Remote;
import java.time.LocalDateTime;
@Remote
public interface ShipmentTrackingServiceRemote {
    ShipmentTrackingResult trackShipment(String trackingNumber) throws ShipmentTrackingException;
    void recordCarrierStatusUpdate(String trackingNumber, ShipmentStatus newStatus,
                                    LocalDateTime actualDelivery) throws ShipmentTrackingException;
}
