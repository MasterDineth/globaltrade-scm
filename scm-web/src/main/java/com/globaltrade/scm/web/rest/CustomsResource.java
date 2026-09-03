package com.globaltrade.scm.web.rest;

import com.globaltrade.scm.common.enums.CustomsDocumentStatus;
import com.globaltrade.scm.common.enums.CustomsDocumentType;
import com.globaltrade.scm.entity.CustomsDocument;
import com.globaltrade.scm.exception.CustomsComplianceException;
import com.globaltrade.scm.service.local.CustomsDocumentationServiceLocal;
import jakarta.ejb.EJB;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
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
 * REST facade over {@link CustomsDocumentationServiceLocal}. Filing a new
 * document ({@code fileDocument}) is deliberately NOT exposed here: its
 * signature takes a {@code Shipment} entity, not just an id, because it is
 * meant to be called from within an existing shipment-handling workflow
 * (see {@code OrderProcessingServiceBean}) that already has that entity in
 * hand, not invoked standalone from an HTTP request that would first need
 * to look one up. Exposing the review lifecycle (submit/approve/reject)
 * here is sufficient to demonstrate the role-gated, audited workflow this
 * assignment's customs-compliance requirement is about.
 */
@Path("/customs-documents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CustomsResource {

    @EJB
    private CustomsDocumentationServiceLocal customsDocumentationService;

    @GET
    @Path("/deadlines")
    public List<CustomsDeadlineView> approachingDeadlines(
            @QueryParam("withinHours") @DefaultValue("48") int withinHours) {
        return customsDocumentationService.findApproachingDeadlines(withinHours).stream()
                .map(CustomsResource::toView)
                .collect(Collectors.toList());
    }

    @POST
    @Path("/{documentId}/submit")
    public Response submit(@PathParam("documentId") Long documentId) throws CustomsComplianceException {
        customsDocumentationService.submitToCustomsAuthority(documentId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{documentId}/approve")
    public Response approve(@PathParam("documentId") Long documentId, ApprovalRequest request)
            throws CustomsComplianceException {
        customsDocumentationService.approveDocument(documentId, request.complianceNotes());
        return Response.noContent().build();
    }

    @POST
    @Path("/{documentId}/reject")
    public Response reject(@PathParam("documentId") Long documentId, RejectionRequest request)
            throws CustomsComplianceException {
        customsDocumentationService.rejectDocument(documentId, request.reason());
        return Response.noContent().build();
    }

    private static CustomsDeadlineView toView(CustomsDocument document) {
        return new CustomsDeadlineView(
                document.getId(),
                document.getShipment() != null ? document.getShipment().getTrackingNumber() : null,
                document.getDocumentType(),
                document.getStatus(),
                document.getSubmissionDeadline());
    }

    public record CustomsDeadlineView(
            Long documentId, String shipmentTrackingNumber, CustomsDocumentType documentType,
            CustomsDocumentStatus status, LocalDateTime submissionDeadline) {
    }

    public record ApprovalRequest(String complianceNotes) {
    }

    public record RejectionRequest(String reason) {
    }
}
