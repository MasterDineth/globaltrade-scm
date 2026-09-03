package com.globaltrade.scm.web.rest;

import com.globaltrade.scm.exception.SupplyChainSystemException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Safety net for the UNCHECKED system-exception hierarchy (see
 * {@code SupplyChainSystemException}'s own javadoc: "failures the caller
 * cannot meaningfully recover from"). Deliberately a separate
 * {@code @Provider} from {@link SupplyChainExceptionMapper} rather than
 * one mapper trying to handle both: {@code SupplyChainSystemException}
 * extends {@code RuntimeException} directly and shares no common
 * supertype with {@code SupplyChainException} other than
 * {@code Throwable}, so JAX-RS could not dispatch both through a single
 * typed {@code ExceptionMapper<T>} anyway.
 *
 * <p>The response body deliberately omits the exception's own message and
 * stack trace, which may describe internal implementation details not
 * appropriate to hand back to an HTTP client, in favour of a generic
 * message plus a correlation id that a support engineer can grep for in
 * the server-side log entry this mapper writes at {@code SEVERE}.</p>
 */
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
