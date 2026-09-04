package com.globaltrade.scm.recovery;
import com.globaltrade.scm.entity.FailedOperation;
import com.globaltrade.scm.exception.SupplyChainException;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
@Stateless
public class ExceptionRecoveryManager {
    private static final Logger LOGGER = Logger.getLogger(ExceptionRecoveryManager.class.getName());
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MILLIS = 75;
    @PersistenceContext(unitName = "scmPU")
    private EntityManager em;
    public <T> T executeWithRetry(RetryableOperation<T> operation, String operationType, String payloadDescription)
            throws SupplyChainException {
        SupplyChainException lastFailure = null;
        for (int attempt = 1; attempt <= DEFAULT_MAX_ATTEMPTS; attempt++) {
            try {
                return operation.attempt();
            } catch (SupplyChainException failure) {
                lastFailure = failure;
                LOGGER.log(Level.WARNING, "Attempt {0}/{1} failed for {2} ({3}): {4}",
                        new Object[]{attempt, DEFAULT_MAX_ATTEMPTS, operationType, payloadDescription,
                                failure.getMessage()});
                if (attempt < DEFAULT_MAX_ATTEMPTS) {
                    backoff(attempt);
                }
            }
        }
        recordFailure(operationType, payloadDescription,
                lastFailure != null ? lastFailure.getMessage() : "Unknown failure");
        throw lastFailure;
    }
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void recordFailure(String operationType, String payload, String failureReason) {
        FailedOperation failedOperation = new FailedOperation();
        failedOperation.setOperationType(operationType);
        failedOperation.setPayload(payload);
        failedOperation.setFailureReason(failureReason);
        failedOperation.setRetryCount(DEFAULT_MAX_ATTEMPTS);
        failedOperation.setLastAttemptAt(LocalDateTime.now());
        failedOperation.setResolved(false);
        em.persist(failedOperation);
    }
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<FailedOperation> findUnresolvedFailures() {
        TypedQuery<FailedOperation> query = em.createQuery(
                "SELECT f FROM FailedOperation f WHERE f.resolved = false ORDER BY f.lastAttemptAt DESC",
                FailedOperation.class);
        return query.getResultList();
    }
    private void backoff(int attempt) {
        try {
            Thread.sleep(BASE_BACKOFF_MILLIS * attempt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
