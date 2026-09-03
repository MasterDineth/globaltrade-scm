package com.globaltrade.scm.service.local;

import com.globaltrade.scm.common.enums.CustomsDocumentType;
import com.globaltrade.scm.entity.CustomsDocument;
import com.globaltrade.scm.entity.Shipment;
import com.globaltrade.scm.exception.CustomsComplianceException;
import jakarta.ejb.Local;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Local-only: customs filing is an internal, role-gated regulated
 * workflow performed by customs agents and logistics coordinators through
 * this same application, not a capability exposed to an external party.
 */
@Local
public interface CustomsDocumentationServiceLocal {

    CustomsDocument fileDocument(Shipment shipment, CustomsDocumentType documentType,
                                  LocalDateTime submissionDeadline) throws CustomsComplianceException;

    void submitToCustomsAuthority(Long documentId) throws CustomsComplianceException;

    void approveDocument(Long documentId, String complianceNotes) throws CustomsComplianceException;

    void rejectDocument(Long documentId, String reason) throws CustomsComplianceException;

    List<CustomsDocument> findApproachingDeadlines(int withinHours);

    /**
     * Deliberately {@code TransactionAttributeType.MANDATORY} on the bean
     * implementation: this step must never run as its own, standalone unit
     * of work -- it exists specifically to be the last step inside a
     * larger caller-managed transaction (see
     * {@code OrderProcessingServiceBean}, a bean-managed-transaction
     * example) so that shipment registration, inventory reservation and
     * customs clearance either all commit together or all roll back
     * together for an international order. See docs/CRITICAL_ANALYSIS.md,
     * "Transaction attribute selection for different logistics scenarios".
     */
    void finalizeShipmentCustomsClearance(Long shipmentId, Long documentId) throws CustomsComplianceException;
}
