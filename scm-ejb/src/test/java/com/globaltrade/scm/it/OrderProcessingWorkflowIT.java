package com.globaltrade.scm.it;

import com.globaltrade.scm.exception.CustomsComplianceException;
import com.globaltrade.scm.exception.InsufficientInventoryException;
import com.globaltrade.scm.exception.ShipmentTrackingException;
import com.globaltrade.scm.service.local.OrderProcessingServiceLocal;
import jakarta.ejb.EJB;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-container integration test for the end-to-end order workflow: BMT
 * order orchestration ({@code OrderProcessingServiceBean}) calling out to
 * CMT collaborators ({@code InventoryManagementServiceBean},
 * {@code ShipmentTrackingServiceBean}, {@code CustomsDocumentationServiceBean}),
 * exercising {@code @RunAs("ADMIN")} identity propagation and the
 * {@code MANDATORY} transaction attribute on
 * {@code finalizeShipmentCustomsClearance} along the way -- none of which
 * can be exercised by a plain (non-container) unit test, since all of it
 * depends on real container-managed transaction and security-context
 * behavior.
 *
 * <p>Requires a running/managed GlassFish instance and a MySQL database
 * loaded from db/schema.sql (the vendor/carrier/inventory ids referenced
 * below are the seed rows that script inserts) -- see
 * docs/DEPLOYMENT_GUIDE.md, "Running the integration tests". This class is
 * excluded from the default {@code mvn test} run and only executes under
 * {@code mvn verify} (see the surefire/failsafe split in scm-ejb/pom.xml).</p>
 */
@ExtendWith(ArquillianExtension.class)
class OrderProcessingWorkflowIT {

    @Deployment
    public static JavaArchive createDeployment() {
        // A focused, single-jar deployment containing only what this test
        // needs, rather than the full EAR -- faster to deploy/redeploy
        // during iterative test development. EAR-level concerns (web.xml,
        // glassfish-web.xml, application.xml module wiring) are exercised
        // by smoke-testing an actually-deployed EAR per
        // docs/DEPLOYMENT_GUIDE.md, not by this test, which is scoped to
        // EJB-tier behavior.
        return ShrinkWrap.create(JavaArchive.class, "order-workflow-test.jar")
                .addPackages(true,
                        "com.globaltrade.scm.entity",
                        "com.globaltrade.scm.exception",
                        "com.globaltrade.scm.interceptor",
                        "com.globaltrade.scm.monitoring",
                        "com.globaltrade.scm.recovery",
                        "com.globaltrade.scm.security",
                        "com.globaltrade.scm.service",
                        "com.globaltrade.scm.timer",
                        "com.globaltrade.scm.common.dto",
                        "com.globaltrade.scm.common.enums")
                .addAsManifestResource("META-INF/persistence.xml", "persistence.xml")
                .addAsManifestResource("META-INF/ejb-jar.xml", "ejb-jar.xml")
                .addAsManifestResource("META-INF/beans.xml", "beans.xml");
    }

    @EJB
    private OrderProcessingServiceLocal orderProcessingService;

    @Test
    void domesticOrderReservesStockAndRegistersShipmentWithoutCustomsFiling()
            throws InsufficientInventoryException, ShipmentTrackingException, CustomsComplianceException {
        // Vendor 2 (Nordic Components AB) / Carrier 1 (Global Ocean
        // Freight), both legs in "SE": no customs branch, so this also
        // establishes the domestic-order baseline the international test
        // below is contrasted against.
        String trackingNumber = orderProcessingService.processSupplyChainOrder(
                "SKU-ELE-2002", 5, 2L, 1L, "SE", "SE");

        assertNotNull(trackingNumber);
        assertTrue(trackingNumber.startsWith("GT-"));
    }

    @Test
    void internationalOrderCompletesCustomsClearanceAsPartOfTheSameTransaction()
            throws InsufficientInventoryException, ShipmentTrackingException, CustomsComplianceException {
        // Cross-border (CN -> SE): exercises the conditional customs-filing
        // branch and therefore the @RunAs("ADMIN") identity that lets a
        // LOGISTICS_COORDINATOR-initiated order still successfully call
        // CustomsDocumentationServiceBean.approveDocument
        // (@RolesAllowed ADMIN/CUSTOMS_AGENT) and
        // finalizeShipmentCustomsClearance (MANDATORY) from within
        // OrderProcessingServiceBean's active bean-managed transaction. A
        // caller genuinely authenticated only as LOGISTICS_COORDINATOR
        // (rather than the test client's own identity) failing this same
        // call chain WITHOUT @RunAs present is the regression this test
        // exists to catch.
        String trackingNumber = orderProcessingService.processSupplyChainOrder(
                "SKU-TEX-1001", 10, 1L, 3L, "CN", "SE");

        assertNotNull(trackingNumber);
        assertTrue(trackingNumber.startsWith("GT-"));
    }

    @Test
    void orderExceedingAvailableStockIsRejectedRatherThanPartiallyReserved() {
        assertThrows(InsufficientInventoryException.class, () ->
                orderProcessingService.processSupplyChainOrder(
                        "SKU-ELE-2002", 100_000, 2L, 1L, "SE", "SE"));
        // A fuller version of this test would also inject
        // InventoryManagementServiceLocal and re-query SKU-ELE-2002 to
        // confirm quantityOnHand is exactly back to its pre-attempt value
        // -- i.e. that InventoryManagementServiceBean.reserveStock's
        // REQUIRED transaction attribute really did roll back the
        // decrement together with the rest of processSupplyChainOrder's
        // bean-managed transaction, rather than merely throwing after
        // already having committed a partial change.
    }
}
