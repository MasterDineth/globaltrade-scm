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
            LOGGER.log(Level.WARNING, "Failed to record audit entry for {0}: {1}",
                    new Object[]{method, e.getMessage()});
        }
    }
    private String resolveCallerPrincipal() {
        try {
            Principal principal = sessionContext.getCallerPrincipal();
            return principal != null ? principal.getName() : "SYSTEM";
        } catch (IllegalStateException e) {
            return "SYSTEM";
        }
    }
}
