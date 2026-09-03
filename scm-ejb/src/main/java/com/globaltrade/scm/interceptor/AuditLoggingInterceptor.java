package com.globaltrade.scm.interceptor;

import com.globaltrade.scm.entity.AuditLogEntry;
import jakarta.annotation.Resource;
import jakarta.ejb.SessionContext;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.InvocationContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * General-purpose audit trail interceptor, bound DECLARATIVELY as a
 * default interceptor for the whole EJB module via
 * {@code META-INF/ejb-jar.xml} rather than with an {@code @Interceptors}
 * annotation on any one class. This is the broadest possible binding
 * scope and is the right call here specifically because every session
 * bean in this module touches regulated trade data, so "log every business
 * method call" is a module-wide policy decision, not a per-class opt-in --
 * exactly the case where XML-declarative binding is preferable to
 * annotations (it can be changed by an ops/compliance team without
 * recompiling a single Java class, and it is impossible to "forget" to
 * apply to a newly added bean).
 *
 * <p>Design trade-off worth calling out explicitly: this interceptor
 * persists the {@link AuditLogEntry} using the SAME persistence context /
 * transaction as the intercepted business method. That gives perfect
 * consistency (an audit row exists if and only if the business change it
 * describes was committed) at the cost of adding the audit-table write to
 * every single business method's transaction and lock footprint. The
 * alternative -- firing the audit write asynchronously (JMS topic, or a
 * {@code @TransactionAttribute(REQUIRES_NEW)} logging service) -- trades
 * that guarantee away for lower latency and less lock contention on the
 * audit table. See docs/CRITICAL_ANALYSIS.md, "Interceptor performance
 * impact", for the full analysis.</p>
 */
public class AuditLoggingInterceptor {

    private static final Logger LOGGER = Logger.getLogger(AuditLoggingInterceptor.class.getName());

    @PersistenceContext(unitName = "scmPU")
    private EntityManager em;

    @Resource
    private SessionContext sessionContext;

    @AroundInvoke
    public Object audit(InvocationContext ctx) throws Exception {
        String method = ctx.getTarget().getClass().getSimpleName() + "#" + ctx.getMethod().getName();
        long start = System.nanoTime();
        try {
            Object result = ctx.proceed();
            recordAudit(method, "SUCCESS", start);
            return result;
        } catch (Exception ex) {
            recordAudit(method, "FAILURE:" + ex.getClass().getSimpleName(), start);
            throw ex;
        }
    }

    private void recordAudit(String method, String outcome, long startNanos) {
        try {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            AuditLogEntry entry = new AuditLogEntry();
            entry.setEntityName(method);
            entry.setAction(outcome);
            entry.setPerformedBy(resolveCallerPrincipal());
            entry.setTimestamp(LocalDateTime.now());
            entry.setDetails("durationMs=" + durationMs);
            em.persist(entry);
        } catch (RuntimeException e) {
            // An audit-logging failure must never break the business call it
            // is observing -- log and swallow rather than propagate.
            LOGGER.log(Level.WARNING, "Failed to record audit entry for {0}: {1}",
                    new Object[]{method, e.getMessage()});
        }
    }

    private String resolveCallerPrincipal() {
        try {
            Principal principal = sessionContext.getCallerPrincipal();
            return principal != null ? principal.getName() : "SYSTEM";
        } catch (IllegalStateException e) {
            // Thrown by containers when called from a context with no
            // security identity available (e.g. certain timer callbacks).
            return "SYSTEM";
        }
    }
}
