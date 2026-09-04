package com.globaltrade.scm.service.local;
import com.globaltrade.scm.common.dto.VendorPerformanceSummary;
import com.globaltrade.scm.entity.Vendor;
import com.globaltrade.scm.exception.VendorDataValidationException;
import jakarta.ejb.Local;
import java.util.concurrent.Future;
@Local
public interface VendorPerformanceServiceLocal {
    VendorPerformanceSummary getVendorPerformanceSummary(Long vendorId) throws VendorDataValidationException;
    Vendor registerVendor(Vendor vendor) throws VendorDataValidationException;
    void updateVendorProfile(Long vendorId, String contactEmail) throws VendorDataValidationException;
    void submitVendorPerformanceReview(Long vendorId, int score, String notes) throws VendorDataValidationException;
    Future<VendorPerformanceSummary> assessVendorAsync(Long vendorId) throws VendorDataValidationException;
}
