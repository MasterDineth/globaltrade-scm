package com.globaltrade.scm.service.local;
import com.globaltrade.scm.common.enums.CustomsDocumentType;
import com.globaltrade.scm.entity.CustomsDocument;
import com.globaltrade.scm.entity.Shipment;
import com.globaltrade.scm.exception.CustomsComplianceException;
import jakarta.ejb.Local;
import java.time.LocalDateTime;
import java.util.List;
@Local
public interface CustomsDocumentationServiceLocal {
    CustomsDocument fileDocument(Shipment shipment, CustomsDocumentType documentType,
                                  LocalDateTime submissionDeadline) throws CustomsComplianceException;
    void submitToCustomsAuthority(Long documentId) throws CustomsComplianceException;
    void approveDocument(Long documentId, String complianceNotes) throws CustomsComplianceException;
    void rejectDocument(Long documentId, String reason) throws CustomsComplianceException;
    List<CustomsDocument> findApproachingDeadlines(int withinHours);
    void finalizeShipmentCustomsClearance(Long shipmentId, Long documentId) throws CustomsComplianceException;
}
