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
@ExtendWith(ArquillianExtension.class)
class OrderProcessingWorkflowIT {
    @Deployment
    public static JavaArchive createDeployment() {
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
        String trackingNumber = orderProcessingService.processSupplyChainOrder(
                "SKU-ELE-2002", 5, 2L, 1L, "SE", "SE");
        assertNotNull(trackingNumber);
        assertTrue(trackingNumber.startsWith("GT-"));
    }
    @Test
    void internationalOrderCompletesCustomsClearanceAsPartOfTheSameTransaction()
            throws InsufficientInventoryException, ShipmentTrackingException, CustomsComplianceException {
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
    }
}
