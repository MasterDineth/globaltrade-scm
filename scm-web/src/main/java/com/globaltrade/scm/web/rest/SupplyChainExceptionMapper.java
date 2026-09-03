package com.globaltrade.scm.web.rest;

import com.globaltrade.scm.exception.CarrierSystemUnavailableException;
import com.globaltrade.scm.exception.CustomsComplianceException;
import com.globaltrade.scm.exception.InsufficientInventoryException;
import com.globaltrade.scm.exception.ShipmentTrackingException;
import com.globaltrade.scm.exception.SupplyChainException;
import com.globaltrade.scm.exception.VendorDataValidationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Translates the checked application-exception hierarchy into HTTP
 * responses. The status chosen for each type always reflects that
 * exception's own {@code @ApplicationException(rollback=...)} semantics
 * and underlying business condition -- see docs/CRITICAL_ANALYSIS.md,
 * "Application exceptions vs. system exceptions in supply chain
 * contexts" -- rather than collapsing everything to a generic 400/500.
 * Deliberately builds {@link Response} with a raw status code
 * ({@code Response.status(int)}) instead of the
 * {@code Response.Status} enum for 422: Unprocessable Entity is not one
 * of the base HTTP/1.1 codes the enum defines, and
 * {@code Response.Status.fromStatusCode(422)} would return {@code null}
 * for it rather than throwing, which is an easy way to accidentally ship
 * a {@code NullPointerException} inside an exception mapper.
 */
@Provider
public class SupplyChainExceptionMapper implements ExceptionMapper<SupplyChainException> {

    @Override
    public Response toResponse(SupplyChainException exception) {
        int statusCode;
        if (exception instanceof ShipmentTrackingException) {
            statusCode = 404;
        } else if (exception instanceof InsufficientInventoryException) {
            statusCode = 409;
        } else if (exception instanceof CarrierSystemUnavailableException) {
            statusCode = 503;
        } else if (exception instanceof CustomsComplianceException
                || exception instanceof VendorDataValidationException) {
            statusCode = 422;
        } else {
            statusCode = 400;
        }
        return Response.status(statusCode)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorBody(exception.getClass().getSimpleName(), exception.getMessage()))
                .build();
    }

    public record ErrorBody(String type, String message) {
    }
}
