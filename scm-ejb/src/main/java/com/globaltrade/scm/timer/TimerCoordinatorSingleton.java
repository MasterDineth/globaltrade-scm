package com.globaltrade.scm.timer;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.DependsOn;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.logging.Logger;
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
