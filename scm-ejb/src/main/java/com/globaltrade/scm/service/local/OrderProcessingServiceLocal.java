package com.globaltrade.scm.service.local;
import com.globaltrade.scm.exception.CustomsComplianceException;
import com.globaltrade.scm.exception.InsufficientInventoryException;
import com.globaltrade.scm.exception.ShipmentTrackingException;
import jakarta.ejb.Local;
@Local
public interface OrderProcessingServiceLocal {
    String processSupplyChainOrder(String sku, int quantity, Long vendorId, Long carrierId,
                                    String originCountry, String destinationCountry)
            throws InsufficientInventoryException, ShipmentTrackingException, CustomsComplianceException;
}
