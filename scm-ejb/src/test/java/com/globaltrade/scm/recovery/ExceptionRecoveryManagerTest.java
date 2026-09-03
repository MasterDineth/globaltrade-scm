package com.globaltrade.scm.recovery;

import com.globaltrade.scm.exception.CarrierSystemUnavailableException;
import com.globaltrade.scm.exception.SupplyChainException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Container-free unit tests for {@link ExceptionRecoveryManager#executeWithRetry}'s
 * retry/backoff control flow, exercised by mocking the injected
 * {@link EntityManager} ({@code @InjectMocks} performs the field injection
 * {@code @PersistenceContext} would otherwise require a container for).
 * Appropriate here because this method's interesting behavior -- retry
 * counting, when {@code recordFailure} fires, what gets rethrown -- is
 * plain Java control flow independent of any real persistence context.
 * Contrast with {@code it/OrderProcessingWorkflowIT}, which needs a real
 * container and database to verify this class's actual JPA/transaction
 * behavior ({@code recordFailure}'s {@code REQUIRES_NEW} durability
 * guarantee cannot be observed against a mock).
 */
@ExtendWith(MockitoExtension.class)
class ExceptionRecoveryManagerTest {

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ExceptionRecoveryManager recoveryManager;

    @Test
    void succeedsOnFirstAttemptWithoutRecordingFailure() throws SupplyChainException {
        String result = recoveryManager.executeWithRetry(() -> "ok", "TEST_OP", "payload");

        assertEquals("ok", result);
        verify(entityManager, never()).persist(any());
    }

    @Test
    void succeedsAfterTransientFailuresWithoutRecordingFailure() throws SupplyChainException {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = recoveryManager.executeWithRetry(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new CarrierSystemUnavailableException("simulated transient failure");
            }
            return "recovered";
        }, "TEST_OP", "payload");

        assertEquals("recovered", result);
        assertEquals(3, attempts.get());
        verify(entityManager, never()).persist(any());
    }

    @Test
    void recordsDeadLetterAndRethrowsTheOriginalFailureAfterExhaustingRetries() {
        CarrierSystemUnavailableException permanentFailure =
                new CarrierSystemUnavailableException("permanently unavailable");

        SupplyChainException thrown = assertThrows(SupplyChainException.class, () ->
                recoveryManager.executeWithRetry(() -> {
                    throw permanentFailure;
                }, "TEST_OP", "payload"));

        // The exact instance, not just an equivalent one: callers of
        // executeWithRetry catch specific SupplyChainException subtypes
        // (e.g. ShipmentStatusUpdateTimerBean catches the general
        // SupplyChainException base), so wrapping or replacing the
        // original exception here would break that contract.
        assertSame(permanentFailure, thrown);
        verify(entityManager, times(1)).persist(any());
    }

    @Test
    void findUnresolvedFailuresDelegatesToEntityManagerQuery() {
        jakarta.persistence.TypedQuery<com.globaltrade.scm.entity.FailedOperation> mockQuery =
                org.mockito.Mockito.mock(jakarta.persistence.TypedQuery.class);
        org.mockito.Mockito.when(entityManager.createQuery(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(com.globaltrade.scm.entity.FailedOperation.class)))
                .thenReturn(mockQuery);
        org.mockito.Mockito.when(mockQuery.getResultList()).thenReturn(java.util.List.of());

        assertEquals(java.util.List.of(), recoveryManager.findUnresolvedFailures());
    }
}
