package com.globaltrade.scm.service.remote;

import com.globaltrade.scm.common.dto.ShipmentTrackingResult;
import com.globaltrade.scm.common.enums.ShipmentStatus;
import com.globaltrade.scm.exception.ShipmentTrackingException;
import jakarta.ejb.Remote;

import java.time.LocalDateTime;

/**
 * Remote-facing subset of shipment tracking: the two capabilities a party
 * OUTSIDE this EAR (a carrier's status-update feed, a customer- or
 * vendor-portal client running in a different process/JVM) legitimately
 * needs. Every other shipment operation (registration, cancellation,
 * internal reporting queries) stays on
 * {@link com.globaltrade.scm.service.local.ShipmentTrackingServiceLocal}
 * only. Deliberately narrowing the remote surface this way matters because
 * RMI/IIOP invocation pays for argument marshalling and sits behind a
 * coarser-grained network security boundary on every call, so every extra
 * method exposed remotely is one more entry point that must be defended
 * against a network-adjacent, only-partially-trusted caller. Note both
 * methods here use the same signatures (including return types) as their
 * counterparts on the Local interface -- required because a single bean
 * class implements both interfaces, and Java resolves a method by name and
 * parameter types only, so two same-named methods with differing return
 * types would be a compile-time erasure clash.
 */
@Remote
public interface ShipmentTrackingServiceRemote {

    ShipmentTrackingResult trackShipment(String trackingNumber) throws ShipmentTrackingException;

    void recordCarrierStatusUpdate(String trackingNumber, ShipmentStatus newStatus,
                                    LocalDateTime actualDelivery) throws ShipmentTrackingException;
}
