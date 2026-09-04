package com.globaltrade.scm.interceptor;
import com.globaltrade.scm.entity.Vendor;
import com.globaltrade.scm.exception.VendorDataValidationException;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.InvocationContext;
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
