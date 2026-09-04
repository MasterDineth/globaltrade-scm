package com.globaltrade.scm.interceptor;
import com.globaltrade.scm.monitoring.MetricsRegistry;
import jakarta.ejb.EJB;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.InvocationContext;
import java.util.logging.Level;
import java.util.logging.Logger;
public class PerformanceMonitoringInterceptor {
    private static final Logger LOGGER = Logger.getLogger(PerformanceMonitoringInterceptor.class.getName());
    private static final long SLOW_THRESHOLD_MS = 0L;
    @EJB
    private MetricsRegistry metricsRegistry;
    @AroundInvoke
    public Object monitor(InvocationContext ctx) throws Exception {
        String methodName = ctx.getTarget().getClass().getSimpleName() + "#" + ctx.getMethod().getName();
        long start = System.nanoTime();
        try {
            return ctx.proceed();
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            if (metricsRegistry != null) {
                metricsRegistry.increment(methodName + ".invocations");
            }
            if (durationMs > SLOW_THRESHOLD_MS) {
                if (metricsRegistry != null) {
                    metricsRegistry.recordSlowInvocation(methodName);
                }
                LOGGER.log(Level.WARNING, "Slow invocation: {0} took {1}ms (threshold {2}ms)",
                        new Object[]{methodName, durationMs, SLOW_THRESHOLD_MS});
            }
        }
    }
}
