package com.globaltrade.scm.service.remote;

import com.globaltrade.scm.common.dto.VendorPerformanceSummary;
import com.globaltrade.scm.exception.VendorDataValidationException;
import jakarta.ejb.Remote;

import java.util.concurrent.Future;

/**
 * Vendor-portal-facing operations. {@code assessVendorAsync} returns a
 * {@code Future<VendorPerformanceSummary>} across the remote boundary --
 * a supported EJB 3.1+ pattern (the container returns a client-side
 * {@code Future} proxy backed by {@code jakarta.ejb.AsyncResult} on the
 * server side) that lets a vendor-portal client kick off a potentially
 * slow aggregation and poll for the result instead of blocking a
 * synchronous remote call.
 */
@Remote
public interface VendorPerformanceServiceRemote {

    VendorPerformanceSummary getVendorPerformanceSummary(Long vendorId) throws VendorDataValidationException;

    void updateVendorProfile(Long vendorId, String contactEmail) throws VendorDataValidationException;

    Future<VendorPerformanceSummary> assessVendorAsync(Long vendorId) throws VendorDataValidationException;
}
