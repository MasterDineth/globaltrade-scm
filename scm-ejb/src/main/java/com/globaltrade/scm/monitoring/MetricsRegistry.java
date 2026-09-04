package com.globaltrade.scm.monitoring;
import jakarta.ejb.ConcurrencyManagement;
import jakarta.ejb.ConcurrencyManagementType;
import jakarta.ejb.Lock;
import jakarta.ejb.LockType;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
@Singleton
@Startup
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
public class MetricsRegistry {
    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> slowInvocationCounters = new ConcurrentHashMap<>();
    public void increment(String metricName) {
        counters.computeIfAbsent(metricName, k -> new LongAdder()).increment();
    }
    public void recordSlowInvocation(String methodName) {
        slowInvocationCounters.computeIfAbsent(methodName, k -> new LongAdder()).increment();
    }
    public long getCount(String metricName) {
        LongAdder adder = counters.get(metricName);
        return adder == null ? 0L : adder.sum();
    }
    public long getSlowInvocationCount(String methodName) {
        LongAdder adder = slowInvocationCounters.get(methodName);
        return adder == null ? 0L : adder.sum();
    }
    public Map<String, Long> snapshotCounters() {
        Map<String, Long> snapshot = new ConcurrentHashMap<>();
        counters.forEach((k, v) -> snapshot.put(k, v.sum()));
        return snapshot;
    }
}
