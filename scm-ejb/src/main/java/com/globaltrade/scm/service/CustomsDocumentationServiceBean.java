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

/**
 * Class-level {@link VendorDataValidationInterceptor}: filings frequently
 * originate from or reference vendor-supplied shipment data, so the same
 * uniform "validate vendor-shaped input before any business logic runs"
 * policy applied to {@code VendorPerformanceServiceBean} applies here too
 * (see that interceptor's javadoc, which names both beans explicitly).
 */
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
        em.flush(); // guarantee the generated id is available before scheduling the reminder timer below

        // Programmatic, per-document timer -- see CustomsDeadlineTimerBean's
        // own javadoc for why this must be created here, at the moment the
        // document (and its business-specific deadline) becomes known,
        // rather than expressed as a fixed calendar @Schedule.
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

    /**
     * {@code @Interceptors(SecurityAuditInterceptor.class)}: approval is
     * the point at which a customs filing becomes legally binding on
     * GlobalTrade's behalf, so who approved it and when must be captured
     * on the dedicated security-audit log stream in addition to the
     * general-purpose record {@code AuditLoggingInterceptor} already
     * writes for every method on this bean (see the interceptor-ordering
     * discussion on {@code SecurityAuditInterceptor}'s own javadoc).
     */
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
        // LEFT JOIN FETCH d.shipment: CustomsDocument.shipment is
        // FetchType.LAZY, and this method runs NOT_SUPPORTED -- the same
        // "safe to read after the persistence context has ended" reasoning
        // documented on ShipmentTrackingServiceBean.findActiveShipments
        // applies here, since callers of this method (the REST layer's
        // customs-deadlines dashboard endpoint) need the associated
        // shipment's tracking number.
        TypedQuery<CustomsDocument> query = em.createQuery(
                "SELECT d FROM CustomsDocument d LEFT JOIN FETCH d.shipment "
                        + "WHERE d.submissionDeadline <= :cutoff AND d.status NOT IN :resolved",
                CustomsDocument.class);
        query.setParameter("cutoff", LocalDateTime.now().plusHours(withinHours));
        query.setParameter("resolved", List.of(CustomsDocumentStatus.SUBMITTED, CustomsDocumentStatus.APPROVED));
        return query.getResultList();
    }

    /**
     * {@code TransactionAttributeType.MANDATORY}: this step must never run
     * as its own, standalone unit of work -- it exists specifically to be
     * the last step inside a larger caller-managed transaction (see
     * {@code OrderProcessingServiceBean}, the bean-managed-transaction
     * example) so that shipment registration, inventory reservation and
     * customs clearance either all commit together or all roll back
     * together for a given international order. Calling this method
     * outside of an active transaction is a deployment/programming error,
     * not a business condition, so the container throws
     * {@code EJBTransactionRequiredException} rather than this method
     * needing to check for one itself. See docs/CRITICAL_ANALYSIS.md,
     * "Transaction attribute selection for different logistics scenarios".
     */
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
