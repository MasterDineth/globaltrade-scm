package com.globaltrade.scm.interceptor;

import com.globaltrade.scm.entity.Vendor;
import com.globaltrade.scm.exception.VendorDataValidationException;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.InvocationContext;

/**
 * Bound with {@code @Interceptors(VendorDataValidationInterceptor.class)}
 * at the CLASS level on every session bean whose public methods accept
 * vendor-supplied data ({@code VendorPerformanceServiceBean},
 * {@code CustomsDocumentationServiceBean}). Class-level binding is the
 * right granularity here because the validation rule ("vendor input must
 * be well-formed before ANY business logic touches it") applies uniformly
 * to every method on those beans -- tagging each method individually with
 * {@code @Interceptors} would be repetitive and, worse, would silently
 * stop protecting a method that a future developer adds without
 * remembering the annotation. Compare with
 * {@link PerformanceMonitoringInterceptor}, which is deliberately
 * method-level because its concern (hot-path latency) is NOT uniform
 * across a bean's methods.
 *
 * <p>Every business method this interceptor guards is required to declare
 * {@code throws VendorDataValidationException} so the checked exception
 * propagates to the caller unchanged instead of being wrapped by the
 * container as an {@code EJBException}.</p>
 */
public class VendorDataValidationInterceptor {

    @AroundInvoke
    public Object validate(InvocationContext ctx) throws Exception {
        for (Object parameter : ctx.getParameters()) {
            if (parameter instanceof Vendor) {
                validateVendor((Vendor) parameter);
            } else if (parameter instanceof Long && ctx.getMethod().getName().toLowerCase().contains("vendor")) {
                validateVendorId((Long) parameter);
            }
        }
        return ctx.proceed();
    }

    private void validateVendor(Vendor vendor) throws VendorDataValidationException {
        if (vendor.getName() == null || vendor.getName().isBlank()) {
            throw new VendorDataValidationException("Vendor name must not be blank");
        }
        if (vendor.getCountry() == null || vendor.getCountry().getCode() == null || vendor.getCountry().getCode().length() != 2) {
            throw new VendorDataValidationException(
                    "Vendor country must be a 2-letter ISO code, got: " + 
                    (vendor.getCountry() != null ? vendor.getCountry().getCode() : "null"));
        }
        if (vendor.getPerformanceScore() != null
                && (vendor.getPerformanceScore() < 0.0 || vendor.getPerformanceScore() > 100.0)) {
            throw new VendorDataValidationException(
                    "Vendor performance score out of range [0,100]: " + vendor.getPerformanceScore());
        }
    }

    private void validateVendorId(Long vendorId) throws VendorDataValidationException {
        if (vendorId == null || vendorId <= 0) {
            throw new VendorDataValidationException("Vendor id must be a positive identifier, got: " + vendorId);
        }
    }
}
