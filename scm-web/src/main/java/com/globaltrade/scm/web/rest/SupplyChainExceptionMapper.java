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
