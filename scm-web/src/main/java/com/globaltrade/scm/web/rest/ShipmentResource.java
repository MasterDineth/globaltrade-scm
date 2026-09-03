package com.globaltrade.scm.web.rest;

import com.globaltrade.scm.common.dto.ShipmentTrackingResult;
import com.globaltrade.scm.common.enums.ShipmentStatus;
import com.globaltrade.scm.entity.Shipment;
import com.globaltrade.scm.exception.ShipmentTrackingException;
import com.globaltrade.scm.service.local.ShipmentTrackingServiceLocal;
import jakarta.ejb.EJB;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST facade over {@link ShipmentTrackingServiceLocal}. Every response
 * body here is either the shared {@link ShipmentTrackingResult} DTO or a
 * resource-local record -- never a JPA {@code Shipment} directly. This
 * matters even though the underlying call is LOCAL (same JVM, where
 * returning an entity to the calling *bean* is often fine -- see
 * {@code VendorPerformanceServiceLocal}'s javadoc on that point): the
 * entity would next be handed to the JAX-RS runtime for JSON
 * serialization, which happens *after* this method's own EJB call has
 * already returned, i.e. outside whatever persistence context serviced
 * it. {@code vendor}/{@code carrier} are {@code FetchType.LAZY} on
 * {@code Shipment}, so the service-layer queries behind
 * {@link ShipmentTrackingServiceLocal#trackShipment} and
 * {@link ShipmentTrackingServiceLocal#findActiveShipments} both use
 * {@code LEFT JOIN FETCH} for exactly this reason -- see those methods'
 * own javadoc in {@code ShipmentTrackingServiceBean}.
 */
@Path("/shipments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ShipmentResource {

    @EJB
    private ShipmentTrackingServiceLocal shipmentTrackingService;

    @GET
    @Path("/{trackingNumber}")
    public ShipmentTrackingResult track(@PathParam("trackingNumber") String trackingNumber)
            throws ShipmentTrackingException {
        return shipmentTrackingService.trackShipment(trackingNumber);
    }

    @GET
    @Path("/active")
    public List<ShipmentTrackingResult> active() {
        return shipmentTrackingService.findActiveShipments().stream()
                .map(ShipmentResource::toResult)
                .collect(Collectors.toList());
    }

    @POST
    public Response register(ShipmentRegistrationRequest request) throws ShipmentTrackingException {
        Shipment shipment = shipmentTrackingService.registerShipment(
                request.trackingNumber(), request.vendorId(), request.carrierId(),
                request.originCountry(), request.destinationCountry(), request.weightKg(),
                request.estimatedDelivery());
        return Response.status(Response.Status.CREATED).entity(toResult(shipment)).build();
    }

    @POST
    @Path("/carrier-webhook")
    public Response carrierWebhook(CarrierStatusWebhookRequest request) throws ShipmentTrackingException {
        // In production this endpoint sits behind carrier-specific API-key
        // authentication mapped to a JAAS identity distinct from the
        // interactive-user roles used by the rest of this resource -- see
        // docs/DEPLOYMENT_GUIDE.md.
        shipmentTrackingService.recordCarrierStatusUpdate(
                request.trackingNumber(), request.status(), request.actualDelivery());
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{trackingNumber}")
    public Response cancel(@PathParam("trackingNumber") String trackingNumber,
                            @QueryParam("reason") String reason) throws ShipmentTrackingException {
        shipmentTrackingService.cancelShipment(trackingNumber, reason);
        return Response.noContent().build();
    }

    private static ShipmentTrackingResult toResult(Shipment shipment) {
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

    public record ShipmentRegistrationRequest(
            String trackingNumber, Long vendorId, Long carrierId, String originCountry,
            String destinationCountry, Double weightKg, LocalDateTime estimatedDelivery) {
    }

    public record CarrierStatusWebhookRequest(
            String trackingNumber, ShipmentStatus status, LocalDateTime actualDelivery) {
    }
}
