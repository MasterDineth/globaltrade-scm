package com.globaltrade.scm.web.rest;
import com.globaltrade.scm.exception.SupplyChainSystemException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
@Provider
public class SupplyChainSystemExceptionMapper implements ExceptionMapper<SupplyChainSystemException> {
    private static final Logger LOGGER = Logger.getLogger(SupplyChainSystemExceptionMapper.class.getName());
    @Override
    public Response toResponse(SupplyChainSystemException exception) {
        String correlationId = UUID.randomUUID().toString();
        LOGGER.log(Level.SEVERE, "Unhandled system exception [correlationId=" + correlationId + "]", exception);
        return Response.status(500)
                .type(MediaType.APPLICATION_JSON)
                .entity(new SupplyChainExceptionMapper.ErrorBody(
                        "SYSTEM_ERROR", "An internal error occurred. Reference: " + correlationId))
                .build();
    }
}
