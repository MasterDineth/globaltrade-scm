package com.globaltrade.scm.service;
import com.globaltrade.scm.common.enums.CustomsDocumentType;
import com.globaltrade.scm.entity.CustomsDocument;
import com.globaltrade.scm.entity.Shipment;
import com.globaltrade.scm.exception.CustomsComplianceException;
import com.globaltrade.scm.exception.InsufficientInventoryException;
import com.globaltrade.scm.exception.ShipmentTrackingException;
import com.globaltrade.scm.service.local.CustomsDocumentationServiceLocal;
import com.globaltrade.scm.service.local.InventoryManagementServiceLocal;
import com.globaltrade.scm.service.local.OrderProcessingServiceLocal;
import com.globaltrade.scm.service.local.ShipmentTrackingServiceLocal;
import jakarta.annotation.Resource;
import jakarta.annotation.security.RolesAllowed;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.UserTransaction;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
@Stateless
@TransactionManagement(TransactionManagementType.BEAN)
@RunAs("ADMIN")
public class OrderProcessingServiceBean implements OrderProcessingServiceLocal {
    private static final Logger LOGGER = Logger.getLogger(OrderProcessingServiceBean.class.getName());
    private static final int TRANSACTION_TIMEOUT_SECONDS = 60;
    @Resource
    private UserTransaction userTransaction;
    @EJB
    private InventoryManagementServiceLocal inventoryManagementService;
    @EJB
    private ShipmentTrackingServiceLocal shipmentTrackingService;
    @EJB
    private CustomsDocumentationServiceLocal customsDocumentationService;
    @Override
    @RolesAllowed({"ADMIN", "LOGISTICS_COORDINATOR"})
    public String processSupplyChainOrder(String sku, int quantity, Long vendorId, Long carrierId,
                                           String originCountry, String destinationCountry)
            throws InsufficientInventoryException, ShipmentTrackingException, CustomsComplianceException {
        boolean internationalShipment =
                originCountry != null && !originCountry.equalsIgnoreCase(destinationCountry);
        String trackingNumber = "GT-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        try {
            userTransaction.setTransactionTimeout(TRANSACTION_TIMEOUT_SECONDS);
            userTransaction.begin();
            inventoryManagementService.reserveStock(sku, quantity);
            Shipment shipment = shipmentTrackingService.registerShipment(
                    trackingNumber, vendorId, carrierId, originCountry, destinationCountry,
                    null, LocalDateTime.now().plusDays(7));
            if (internationalShipment) {
                CustomsDocument document = customsDocumentationService.fileDocument(
                        shipment, CustomsDocumentType.COMMERCIAL_INVOICE, LocalDateTime.now().plusDays(2));
                customsDocumentationService.submitToCustomsAuthority(document.getId());
                customsDocumentationService.approveDocument(
                        document.getId(), "Auto-approved as part of order-processing workflow.");
                customsDocumentationService.finalizeShipmentCustomsClearance(shipment.getId(), document.getId());
            }
            userTransaction.commit();
            return trackingNumber;
        } catch (InsufficientInventoryException | ShipmentTrackingException | CustomsComplianceException businessFailure) {
            safeRollback();
            throw businessFailure;
        } catch (NotSupportedException | SystemException | RollbackException
                | HeuristicMixedException | HeuristicRollbackException transactionInfrastructureFailure) {
            safeRollback();
            throw new ShipmentTrackingException(
                    "Order could not be processed due to a transaction management failure: "
                            + transactionInfrastructureFailure.getMessage());
        } catch (RuntimeException unexpected) {
            safeRollback();
            throw unexpected;
        }
    }
    private void safeRollback() {
        try {
            if (userTransaction.getStatus() != Status.STATUS_NO_TRANSACTION) {
                userTransaction.rollback();
            }
        } catch (SystemException e) {
            LOGGER.log(Level.SEVERE, "Failed to roll back order-processing transaction after a business failure", e);
        }
    }
}
