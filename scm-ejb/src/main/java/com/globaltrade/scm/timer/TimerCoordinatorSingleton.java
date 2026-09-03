package com.globaltrade.scm.timer;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.DependsOn;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Node-identity / heartbeat registration for this application server
 * instance, and the home for the timer-clustering discussion referenced
 * throughout this package's javadoc.
 *
 * <p><b>Why this matters:</b> EJB persistent timers are, by default,
 * tracked in a timer store local to the instance that created them --
 * GlassFish does not transparently fail a timer over to a surviving
 * cluster member if the owning instance goes down, and if every cluster
 * member independently runs the {@code @Startup} logic in
 * {@link VendorPerformanceAssessmentTimerBean}, each one would try to
 * register its own copy of the same recurring timer, producing duplicate
 * monthly assessments. Two production-grade resolutions exist beyond the
 * scope of a single bean:
 * <ol>
 *   <li>Designate exactly one cluster member as the "timer node" (e.g. via
 *       deployment target / an external leader-election service such as a
 *       ZooKeeper or DB-advisory-lock-backed coordinator) and gate
 *       programmatic timer registration behind "am I the designated node?"</li>
 *   <li>Externalize recurring, cluster-wide scheduling entirely, to a
 *       dedicated distributed scheduler (e.g. a clustered Quartz
 *       instance backed by a shared JDBC job store, or a cloud
 *       cron/message-queue trigger invoking a stateless business
 *       method) so no single application-server instance owns timer
 *       state at all.</li>
 * </ol>
 * This bean implements the lightweight, always-useful part of that
 * story -- a per-instance heartbeat row any of the above strategies can
 * build on -- rather than a full leader-election protocol, which is
 * infrastructure-dependent and out of scope for application code alone.
 * See docs/CRITICAL_ANALYSIS.md, "Timer persistence and reliability in
 * globally distributed logistics environments", for the full write-up.</p>
 */
@Singleton
@Startup
@DependsOn("MetricsRegistry")
public class TimerCoordinatorSingleton {

    private static final Logger LOGGER = Logger.getLogger(TimerCoordinatorSingleton.class.getName());

    private String nodeId;
    private LocalDateTime startedAt;

    @PostConstruct
    public void registerNode() {
        this.nodeId = resolveNodeId();
        this.startedAt = LocalDateTime.now();
        LOGGER.info(() -> String.format(
                "Timer coordinator online: node=%s startedAt=%s. "
                        + "Reminder: recurring programmatic timers should be gated to a single "
                        + "designated node in a clustered deployment (see class javadoc).",
                nodeId, startedAt));
    }

    private String resolveNodeId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "UNKNOWN_NODE-" + System.currentTimeMillis();
        }
    }

    public String getNodeId() {
        return nodeId;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }
}
