package com.globaltrade.scm.interceptor;

import jakarta.annotation.Resource;
import jakarta.ejb.SessionContext;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.InvocationContext;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Bound with {@code @Interceptors(SecurityAuditInterceptor.class)} at the
 * METHOD level on individually sensitive, authorization-gated operations
 * (customs-document approval, any security override path). Kept separate
 * from {@link AuditLoggingInterceptor} on purpose: general audit logging
 * is an operational/observability concern (module-wide, high volume, fine
 * to lose an entry occasionally), while security auditing is a compliance
 * / non-repudiation concern (must capture exactly who invoked exactly
 * which privileged operation and under which roles, written to a
 * dedicated logger stream that ops can route to a write-once destination
 * independently of general application logs).
 *
 * <p>Ordering matters: when both a class-level/XML-default interceptor and
 * a method-level {@code @Interceptors} interceptor apply to the same
 * method, the Jakarta EE interceptor spec invokes them in this order:
 * XML-declared default interceptors first (outermost), then class-level
 * annotation interceptors, then method-level annotation interceptors
 * (innermost, closest to the target method) -- so
 * {@code AuditLoggingInterceptor} (XML default) always wraps this one.
 * That ordering is intentional: the general audit record is written
 * before we know whether the more specific security-sensitive call
 * itself will additionally be flagged.</p>
 */
public class SecurityAuditInterceptor {

    private static final Logger SECURITY_LOGGER = Logger.getLogger("com.globaltrade.scm.SECURITY_AUDIT");

    @Resource
    private SessionContext sessionContext;

    @AroundInvoke
    public Object auditSecuritySensitiveCall(InvocationContext ctx) throws Exception {
        String caller = resolveCallerPrincipal();
        String method = ctx.getTarget().getClass().getSimpleName() + "#" + ctx.getMethod().getName();
        SECURITY_LOGGER.info(() -> String.format(
                "[%s] SECURITY-SENSITIVE INVOCATION method=%s caller=%s", LocalDateTime.now(), method, caller));
        try {
            Object result = ctx.proceed();
            SECURITY_LOGGER.info(() -> String.format(
                    "[%s] SECURITY-SENSITIVE INVOCATION method=%s caller=%s outcome=GRANTED",
                    LocalDateTime.now(), method, caller));
            return result;
        } catch (Exception ex) {
            SECURITY_LOGGER.warning(() -> String.format(
                    "[%s] SECURITY-SENSITIVE INVOCATION method=%s caller=%s outcome=DENIED_OR_FAILED reason=%s",
                    LocalDateTime.now(), method, caller, ex.getClass().getSimpleName()));
            throw ex;
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
