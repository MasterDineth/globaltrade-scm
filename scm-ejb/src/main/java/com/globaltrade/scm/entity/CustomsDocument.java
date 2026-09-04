package com.globaltrade.scm.entity;
import com.globaltrade.scm.common.enums.CustomsDocumentStatus;
import com.globaltrade.scm.common.enums.CustomsDocumentType;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
@Entity
@Table(name = "customs_document")
public class CustomsDocument implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customs_document_id")
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, unique = true)
    private Shipment shipment;
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32)
    private CustomsDocumentType documentType;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CustomsDocumentStatus status = CustomsDocumentStatus.PENDING;
    @Column(name = "submission_deadline")
    private LocalDateTime submissionDeadline;
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    @Column(name = "compliance_notes", length = 2000)
    private String complianceNotes;
    @Version
    @Column(name = "version")
    private Long version;
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Shipment getShipment() {
        return shipment;
    }
    public void setShipment(Shipment shipment) {
        this.shipment = shipment;
    }
    public CustomsDocumentType getDocumentType() {
        return documentType;
    }
    public void setDocumentType(CustomsDocumentType documentType) {
        this.documentType = documentType;
    }
    public CustomsDocumentStatus getStatus() {
        return status;
    }
    public void setStatus(CustomsDocumentStatus status) {
        this.status = status;
    }
    public LocalDateTime getSubmissionDeadline() {
        return submissionDeadline;
    }
    public void setSubmissionDeadline(LocalDateTime submissionDeadline) {
        this.submissionDeadline = submissionDeadline;
    }
    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }
    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }
    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }
    public String getComplianceNotes() {
        return complianceNotes;
    }
    public void setComplianceNotes(String complianceNotes) {
        this.complianceNotes = complianceNotes;
    }
    public Long getVersion() {
        return version;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomsDocument)) return false;
        CustomsDocument that = (CustomsDocument) o;
        return id != null && id.equals(that.id);
    }
    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
