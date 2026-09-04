package com.globaltrade.scm.service.remote;
import com.globaltrade.scm.common.dto.VendorPerformanceSummary;
import com.globaltrade.scm.exception.VendorDataValidationException;
import jakarta.ejb.Remote;
import java.util.concurrent.Future;
@Remote
public interface VendorPerformanceServiceRemote {
    VendorPerformanceSummary getVendorPerformanceSummary(Long vendorId) throws VendorDataValidationException;
    void updateVendorProfile(Long vendorId, String contactEmail) throws VendorDataValidationException;
    Future<VendorPerformanceSummary> assessVendorAsync(Long vendorId) throws VendorDataValidationException;
}
