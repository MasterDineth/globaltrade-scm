package com.globaltrade.scm.web.rest;
import com.globaltrade.scm.entity.InventoryItem;
import com.globaltrade.scm.service.local.InventoryManagementServiceLocal;
import jakarta.ejb.EJB;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
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
    @GET
    @Path("/all")
    public List<InventoryItem> allStock() {
        return inventoryManagementService.findAll();
    }
    @POST
    public InventoryItem create(InventoryItem item) {
        return inventoryManagementService.createItem(item);
    }
    @PUT
    @Path("/{sku}")
    public InventoryItem update(@PathParam("sku") String sku, InventoryItem item) {
        return inventoryManagementService.updateItem(sku, item);
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
