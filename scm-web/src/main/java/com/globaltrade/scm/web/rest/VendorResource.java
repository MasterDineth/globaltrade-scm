package com.globaltrade.scm.web.rest;
import com.globaltrade.scm.common.dto.VendorPerformanceSummary;
import com.globaltrade.scm.entity.Country;
import com.globaltrade.scm.entity.Vendor;
import com.globaltrade.scm.exception.VendorDataValidationException;
import com.globaltrade.scm.service.local.VendorPerformanceServiceLocal;
import jakarta.ejb.EJB;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
@Path("/vendors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VendorResource {
    private static final int ASSESSMENT_TIMEOUT_SECONDS = 10;
    @EJB
    private VendorPerformanceServiceLocal vendorPerformanceService;
    @GET
    @Path("/{vendorId}/performance")
    public VendorPerformanceSummary performance(@PathParam("vendorId") Long vendorId)
            throws VendorDataValidationException {
        return vendorPerformanceService.getVendorPerformanceSummary(vendorId);
    }
    @PUT
    @Path("/{vendorId}/profile")
    public Response updateProfile(@PathParam("vendorId") Long vendorId, UpdateProfileRequest request)
            throws VendorDataValidationException {
        vendorPerformanceService.updateVendorProfile(vendorId, request.contactEmail());
        return Response.noContent().build();
    }
    @POST
    public Response registerVendor(RegistrationRequest request) throws VendorDataValidationException {
        Vendor vendor = new Vendor();
        vendor.setName(request.name());
        vendor.setContactEmail(request.contactEmail());
        Country c = new Country();
        c.setCode(request.countryCode());
        vendor.setCountry(c);
        vendorPerformanceService.registerVendor(vendor);
        return Response.status(Response.Status.CREATED).build();
    }
    @POST
    @Path("/{vendorId}/reviews")
    public Response submitReview(@PathParam("vendorId") Long vendorId, ReviewRequest request)
            throws VendorDataValidationException {
        vendorPerformanceService.submitVendorPerformanceReview(vendorId, request.score(), request.notes());
        return Response.status(Response.Status.CREATED).build();
    }
    @POST
    @Path("/{vendorId}/assessments")
    public VendorPerformanceSummary assess(@PathParam("vendorId") Long vendorId)
            throws VendorDataValidationException {
        try {
            Future<VendorPerformanceSummary> future = vendorPerformanceService.assessVendorAsync(vendorId);
            return future.get(ASSESSMENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebApplicationException("Assessment was interrupted.", 500);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof VendorDataValidationException validationFailure) {
                throw new WebApplicationException(validationFailure.getMessage(), 422);
            }
            throw new WebApplicationException("Assessment failed.", 500);
        } catch (TimeoutException e) {
            throw new WebApplicationException("Assessment timed out after "
                    + ASSESSMENT_TIMEOUT_SECONDS + "s.", 504);
        }
    }
    public record UpdateProfileRequest(String contactEmail) {
    }
    public record RegistrationRequest(String name, String countryCode, String contactEmail) {
    }
    public record ReviewRequest(int score, String notes) {
    }
}
