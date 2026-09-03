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

/**
 * Bean-Managed Transaction (BMT) example, in deliberate contrast to every
 * other service bean in this package (all Container-Managed). This is the
 * one workflow that genuinely needs BMT's fine-grained control: it calls
 * three independently-transactional collaborators (inventory reservation,
 * shipment registration, and -- conditionally -- customs clearance) and
 * must commit or roll back ALL of them as a single atomic unit, but
 * WHETHER the third call even happens is runtime business logic (is this
 * an international shipment?) that CMT's static, deployment-time
 * transaction attributes cannot express on their own. See
 * docs/CRITICAL_ANALYSIS.md, "Container-managed vs. bean-managed
 * transactions", for the full comparison against simply composing
 * REQUIRED-attributed CMT methods (which would also have worked here, at
 * the cost of losing the explicit, readable begin/commit/rollback boundary
 * this method demonstrates, and the ability to react to a business
 * condition -- international vs. domestic -- before deciding what
 * participates in the transaction at all).
 *
 * <p><b>{@code @RunAs("ADMIN")}:</b> {@link #processSupplyChainOrder} is
 * callable by a {@code LOGISTICS_COORDINATOR}, but its customs-clearance
 * branch calls {@code CustomsDocumentationServiceLocal.approveDocument},
 * which requires {@code CUSTOMS_AGENT} (or {@code ADMIN}) -- a role an
 * ordinary logistics coordinator does not personally hold. Without
 * {@code @RunAs}, that inner call would fail with
 * {@code EJBAccessException} for every caller except an admin, even though
 * approving-as-part-of-this-orchestrated-workflow is exactly what this
 * bean exists to do on the caller's behalf. {@code @RunAs} establishes a
 * single fixed identity ({@code "ADMIN"}) for every outbound call this
 * bean makes, independent of who the interactively-authenticated caller
 * of {@link #processSupplyChainOrder} actually is -- the textbook
 * "trusted orchestrator" use case for this annotation. See
 * docs/CRITICAL_ANALYSIS.md, "Authentication mechanisms for different user
 * types and emergency logistics scenarios".</p>
 */
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

            // Step 1: reserve stock. InventoryManagementServiceBean.reserveStock
            // is CMT/REQUIRED, so it transparently joins the JTA transaction
            // this method just began rather than starting its own -- CMT's
            // REQUIRED semantics do not care whether the transaction it is
            // joining was started by another CMT bean or, as here, by an
            // explicit UserTransaction.begin() in a BMT bean.
            inventoryManagementService.reserveStock(sku, quantity);

            // Step 2: register the shipment (also REQUIRED -> joins).
            Shipment shipment = shipmentTrackingService.registerShipment(
                    trackingNumber, vendorId, carrierId, originCountry, destinationCountry,
                    null, LocalDateTime.now().plusDays(7));

            // Step 3 (conditional): international shipments must clear
            // customs before the order can be considered complete.
            // finalizeShipmentCustomsClearance is MANDATORY -- calling it
            // here, inside this already-active UserTransaction, is exactly
            // the scenario that attribute exists to enforce.
            if (internationalShipment) {
                CustomsDocument document = customsDocumentationService.fileDocument(
                        shipment, CustomsDocumentType.COMMERCIAL_INVOICE, LocalDateTime.now().plusDays(2));
                // Production behaviour would leave the order in a
                // PENDING_CUSTOMS state here and clear it asynchronously
                // once a human customs agent reviews the filing. This
                // reference implementation submits and approves inline so
                // that finalizeShipmentCustomsClearance's MANDATORY
                // precondition (status == APPROVED) is satisfiable within a
                // single call, which keeps the end-to-end workflow
                // demonstrable and testable in one method; the @RunAs
                // identity above is what makes the inline approval call
                // legal regardless of which role actually invoked this
                // method.
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

    /**
     * BMT's central responsibility, and the one piece CMT would otherwise
     * have handled automatically: nothing rolls back a bean-managed
     * transaction except an explicit {@code UserTransaction.rollback()}
     * call. This helper is deliberately defensive -- it checks the
     * transaction is still active before attempting to roll it back, since
     * some failure paths (e.g. {@code commit()} itself throwing
     * {@code HeuristicRollbackException}) can leave no transaction
     * associated with the thread at all by the time this runs.
     */
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
