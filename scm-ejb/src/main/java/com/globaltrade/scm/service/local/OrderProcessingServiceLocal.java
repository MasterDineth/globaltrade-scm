package com.globaltrade.scm.service.local;

import com.globaltrade.scm.exception.CustomsComplianceException;
import com.globaltrade.scm.exception.InsufficientInventoryException;
import com.globaltrade.scm.exception.ShipmentTrackingException;
import jakarta.ejb.Local;

/**
 * Orchestrates a full order: inventory reservation, shipment registration
 * and (for international orders) customs filing, as one bean-managed
 * transaction. See {@code OrderProcessingServiceBean} for why BMT rather
 * than CMT was chosen for this specific workflow.
 */
@Local
public interface OrderProcessingServiceLocal {

    String processSupplyChainOrder(String sku, int quantity, Long vendorId, Long carrierId,
                                    String originCountry, String destinationCountry)
            throws InsufficientInventoryException, ShipmentTrackingException, CustomsComplianceException;
}
