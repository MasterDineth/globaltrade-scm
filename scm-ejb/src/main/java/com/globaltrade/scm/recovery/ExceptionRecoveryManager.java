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

/**
 * Bounded-retry-with-backoff, falling through to a persistent dead-letter
 * record ({@link FailedOperation}) once the retry budget is exhausted.
 * This is the shared recovery strategy referenced by
 * {@code CarrierSystemUnavailableException}'s javadoc and used by
 * {@code ShipmentStatusUpdateTimerBean} to wrap each individual carrier
 * poll: a transient carrier-API blip gets a few fast, in-process retries;
 * a carrier that is genuinely down for the whole polling cycle gets
 * recorded for operations staff to review and manually replay, rather than
 * silently disappearing into a log line no one is watching in real time.
 *
 * <p>See docs/CRITICAL_ANALYSIS.md, "Recovery strategies for different
 * supply chain failure scenarios", for why this bean intentionally does
 * <em>not</em> retry forever, and why the backoff below uses a short,
 * bounded {@code Thread.sleep} rather than either (a) no delay at all, or
 * (b) a timer-based delayed-retry mechanism.</p>
 */
@Stateless
public class ExceptionRecoveryManager {

    private static final Logger LOGGER = Logger.getLogger(ExceptionRecoveryManager.class.getName());

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MILLIS = 75;

    @PersistenceContext(unitName = "scmPU")
    private EntityManager em;

    /**
     * Attempts {@code operation} up to {@link #DEFAULT_MAX_ATTEMPTS} times,
     * with a short linear backoff between attempts. If every attempt fails,
     * records a {@link FailedOperation} dead-letter row and rethrows the
     * last failure to the caller (this method never silently swallows a
     * failure -- the dead-letter record supplements, but does not replace,
     * normal exception propagation).
     *
     * <p><b>On the backoff delay:</b> EJB components are conventionally
     * discouraged from managing threads themselves, since blocking a
     * container-managed thread pool thread works against the container's
     * ability to scale request handling. The sleep here is a deliberate,
     * narrow exception to that guideline: it is bounded (worst case
     * {@code 75 + 150 = 225ms} total across two waits), used only for a
     * small number of genuinely external-system calls (carrier/customs API
     * polls), not on any latency-sensitive interactive request path, and
     * the alternative -- scheduling each retry as its own EJB timer
     * callback -- would trade a bounded, easily-reasoned-about delay for
     * meaningfully more state-management complexity (persisting
     * in-progress retry state between timer firings) for a use case that
     * does not need it. A sustained, minutes-long outage is handled by the
     * dead-letter record below and the next scheduled polling cycle, not
     * by this method retrying longer.</p>
     */
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

    /**
     * {@code REQUIRES_NEW}: the dead-letter record must survive even if
     * the caller's own transaction subsequently rolls back (which it
     * typically will, having just re-thrown {@code lastFailure} above) --
     * a {@link FailedOperation} row inserted in the *same* transaction as
     * the failure it is recording would itself be undone by that
     * transaction's rollback, defeating the entire point of a dead-letter
     * queue. Suspending the caller's transaction and committing this one
     * independently is what makes the record durable regardless of the
     * caller's ultimate outcome.
     */
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

    /**
     * Administrative query backing the dead-letter review workflow: what
     * has failed and not yet been manually resolved. Marking a row
     * resolved (after a human replays or otherwise disposes of it) is a
     * simple {@code em.merge} left to whichever administrative caller
     * performs the replay, rather than duplicated here.
     */
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
