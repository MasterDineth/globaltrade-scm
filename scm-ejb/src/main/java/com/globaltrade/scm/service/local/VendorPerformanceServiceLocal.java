package com.globaltrade.scm.service.local;

import com.globaltrade.scm.common.dto.VendorPerformanceSummary;
import com.globaltrade.scm.entity.Vendor;
import com.globaltrade.scm.exception.VendorDataValidationException;
import jakarta.ejb.Local;

import java.util.concurrent.Future;

/**
 * {@code updateVendorProfile} intentionally returns {@code void} here even
 * though this is the Local interface (where returning the updated
 * {@code Vendor} entity directly would otherwise be perfectly safe): it
 * must have the exact same signature, including return type, as
 * {@link com.globaltrade.scm.service.remote.VendorPerformanceServiceRemote#updateVendorProfile},
 * since one bean class implements both interfaces. Callers needing the
 * post-update state re-fetch via {@link #getVendorPerformanceSummary}.
 */
@Local
public interface VendorPerformanceServiceLocal {

    VendorPerformanceSummary getVendorPerformanceSummary(Long vendorId) throws VendorDataValidationException;

    Vendor registerVendor(Vendor vendor) throws VendorDataValidationException;

    void updateVendorProfile(Long vendorId, String contactEmail) throws VendorDataValidationException;

    void submitVendorPerformanceReview(Long vendorId, int score, String notes) throws VendorDataValidationException;

    Future<VendorPerformanceSummary> assessVendorAsync(Long vendorId) throws VendorDataValidationException;
}
