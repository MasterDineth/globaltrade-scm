package com.globaltrade.scm.web.rest;

import com.globaltrade.scm.entity.InventoryItem;
import com.globaltrade.scm.service.local.InventoryManagementServiceLocal;
import jakarta.ejb.EJB;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * REST facade over {@link InventoryManagementServiceLocal}. Unlike
 * {@code ShipmentResource}, this resource returns {@code InventoryItem}
 * entities directly rather than mapping to a DTO first -- deliberately,
 * not as an oversight: {@code InventoryItem} has no {@code @ManyToOne} /
 * {@code @OneToOne} associations at all (see the entity itself), so there
 * is no lazy-loading hazard to guard against, and a hand-written DTO that
 * would just mirror every field back verbatim adds a maintenance burden
 * (two places to update on every schema change) without adding any
 * safety. The rule this module actually follows is "never let JAX-RS
 * serialize an entity that could carry an uninitialized lazy proxy," not
 * "never return an entity" -- see {@code ShipmentResource}'s javadoc for
 * the case where that distinction matters.
 */
@Path("/inventory")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InventoryResource {

    @EJB
    private InventoryManagementServiceLocal inventoryManagementService;

    @GET
    @Path("/{sku}")
    public InventoryItem getItem(@PathParam("sku") String sku) {
        InventoryItem item = inventoryManagementService.getItem(sku);
        if (item == null) {
            throw new NotFoundException("Unknown SKU: " + sku);
        }
        return item;
    }

    @GET
    @Path("/low-stock")
    public List<InventoryItem> lowStock() {
        return inventoryManagementService.findBelowReorderThreshold();
    }

    @POST
    @Path("/{sku}/replenish")
    public Response replenish(@PathParam("sku") String sku, ReplenishRequest request) {
        inventoryManagementService.replenishStock(sku, request.quantity(), request.sourceReference());
        return Response.noContent().build();
    }

    public record ReplenishRequest(int quantity, String sourceReference) {
    }
}
