package com.globaltrade.scm.service;
import com.globaltrade.scm.common.enums.CustomsDocumentStatus;
import com.globaltrade.scm.common.enums.CustomsDocumentType;
import com.globaltrade.scm.entity.CustomsDocument;
import com.globaltrade.scm.entity.Shipment;
import com.globaltrade.scm.exception.CustomsComplianceException;
import com.globaltrade.scm.interceptor.SecurityAuditInterceptor;
import com.globaltrade.scm.interceptor.VendorDataValidationInterceptor;
import com.globaltrade.scm.service.local.CustomsDocumentationServiceLocal;
import com.globaltrade.scm.timer.CustomsDeadlineTimerBean;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.interceptor.Interceptors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;
@Stateless
@Interceptors(VendorDataValidationInterceptor.class)
public class CustomsDocumentationServiceBean implements CustomsDocumentationServiceLocal {
    @PersistenceContext(unitName = "scmPU")
    private EntityManager em;
    @EJB
    private CustomsDeadlineTimerBean customsDeadlineTimerBean;
    @Override
    @RolesAllowed({"ADMIN", "CUSTOMS_AGENT", "LOGISTICS_COORDINATOR"})
    public CustomsDocument fileDocument(Shipment shipment, CustomsDocumentType documentType,
                                         LocalDateTime submissionDeadline) throws CustomsComplianceException {
        if (shipment == null || shipment.getId() == null) {
            throw new CustomsComplianceException("Cannot file a customs document for an unpersisted shipment.");
        }
        CustomsDocument document = new CustomsDocument();
        document.setShipment(shipment);
        document.setDocumentType(documentType);
        document.setStatus(CustomsDocumentStatus.PENDING);
        document.setSubmissionDeadline(submissionDeadline);
        em.persist(document);
        em.flush(); 
        customsDeadlineTimerBean.scheduleDeadlineReminder(document);
        return document;
    }
    @Override
    @RolesAllowed({"ADMIN", "CUSTOMS_AGENT"})
    public void submitToCustomsAuthority(Long documentId) throws CustomsComplianceException {
        CustomsDocument document = requireDocument(documentId);
        validateReadyForSubmission(document);
        document.setStatus(CustomsDocumentStatus.SUBMITTED);
        document.setSubmittedAt(LocalDateTime.now());
        em.merge(document);
    }
    @Override
    @Interceptors(SecurityAuditInterceptor.class)
    @RolesAllowed({"ADMIN", "CUSTOMS_AGENT"})
    public void approveDocument(Long documentId, String complianceNotes) throws CustomsComplianceException {
        CustomsDocument document = requireDocument(documentId);
        if (document.getStatus() != CustomsDocumentStatus.SUBMITTED
                && document.getStatus() != CustomsDocumentStatus.UNDER_REVIEW) {
            throw new CustomsComplianceException(
                    "Document " + documentId + " cannot be approved from status " + document.getStatus());
        }
        document.setStatus(CustomsDocumentStatus.APPROVED);
        document.setApprovedAt(LocalDateTime.now());
        document.setComplianceNotes(complianceNotes);
        em.merge(document);
    }
    @Override
    @RolesAllowed({"ADMIN", "CUSTOMS_AGENT"})
    public void rejectDocument(Long documentId, String reason) throws CustomsComplianceException {
        CustomsDocument document = requireDocument(documentId);
        document.setStatus(CustomsDocumentStatus.REJECTED);
        document.setComplianceNotes(reason);
        em.merge(document);
    }
    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @RolesAllowed({"ADMIN", "CUSTOMS_AGENT", "LOGISTICS_COORDINATOR"})
    public List<CustomsDocument> findApproachingDeadlines(int withinHours) {
        TypedQuery<CustomsDocument> query = em.createQuery(
                "SELECT d FROM CustomsDocument d LEFT JOIN FETCH d.shipment "
                        + "WHERE d.submissionDeadline <= :cutoff AND d.status NOT IN :resolved",
                CustomsDocument.class);
        query.setParameter("cutoff", LocalDateTime.now().plusHours(withinHours));
        query.setParameter("resolved", List.of(CustomsDocumentStatus.SUBMITTED, CustomsDocumentStatus.APPROVED));
        return query.getResultList();
    }
    @Override
    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    @RolesAllowed({"ADMIN", "LOGISTICS_COORDINATOR"})
    public void finalizeShipmentCustomsClearance(Long shipmentId, Long documentId) throws CustomsComplianceException {
        CustomsDocument document = requireDocument(documentId);
        if (document.getShipment() == null || !document.getShipment().getId().equals(shipmentId)) {
            throw new CustomsComplianceException(
                    "Document " + documentId + " does not belong to shipment " + shipmentId);
        }
        if (document.getStatus() != CustomsDocumentStatus.APPROVED) {
            throw new CustomsComplianceException(
                    "Shipment " + shipmentId + " cannot clear customs: document " + documentId
                            + " is " + document.getStatus() + ", not APPROVED.");
        }
    }
    private void validateReadyForSubmission(CustomsDocument document) throws CustomsComplianceException {
        if (document.getSubmissionDeadline() != null
                && document.getSubmissionDeadline().isBefore(LocalDateTime.now())) {
            throw new CustomsComplianceException(
                    "Document " + document.getId() + " missed its submission deadline of "
                            + document.getSubmissionDeadline());
        }
        Shipment shipment = document.getShipment();
        if (shipment == null || shipment.getOriginCountry() == null || shipment.getDestinationCountry() == null) {
            throw new CustomsComplianceException(
                    "Document " + document.getId() + " is missing required origin/destination country data.");
        }
    }
    private CustomsDocument requireDocument(Long documentId) throws CustomsComplianceException {
        CustomsDocument document = em.find(CustomsDocument.class, documentId);
        if (document == null) {
            throw new CustomsComplianceException("Unknown customs document id: " + documentId);
        }
        return document;
    }
}
