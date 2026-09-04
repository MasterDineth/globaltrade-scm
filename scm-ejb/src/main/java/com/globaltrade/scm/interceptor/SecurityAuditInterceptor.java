package com.globaltrade.scm.interceptor;
import jakarta.annotation.Resource;
import jakarta.ejb.SessionContext;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.InvocationContext;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.logging.Logger;
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
