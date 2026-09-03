package com.globaltrade.scm.web.rest;

import com.globaltrade.scm.exception.CustomsComplianceException;
import com.globaltrade.scm.exception.InsufficientInventoryException;
import com.globaltrade.scm.exception.ShipmentTrackingException;
import com.globaltrade.scm.service.local.OrderProcessingServiceLocal;
import jakarta.ejb.EJB;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST entry point into the bean-managed-transaction order workflow (see
 * {@code OrderProcessingServiceBean}). This is the one resource in this
 * package where a single HTTP call fans out into inventory reservation,
 * shipment registration and, for international orders, a full customs
 * filing/approval/clearance sequence -- all as one atomic unit.
 */
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    @EJB
    private OrderProcessingServiceLocal orderProcessingService;

    @POST
    public Response placeOrder(PlaceOrderRequest request)
            throws InsufficientInventoryException, ShipmentTrackingException, CustomsComplianceException {
        String trackingNumber = orderProcessingService.processSupplyChainOrder(
                request.sku(), request.quantity(), request.vendorId(), request.carrierId(),
                request.originCountry(), request.destinationCountry());
        return Response.status(Response.Status.CREATED)
                .entity(new PlaceOrderResponse(trackingNumber))
                .build();
    }

    public record PlaceOrderRequest(
            String sku, int quantity, Long vendorId, Long carrierId,
            String originCountry, String destinationCountry) {
    }

    public record PlaceOrderResponse(String trackingNumber) {
    }
}
