package org.readybell.platform.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** {@code readybell.outbox.*} */
@ConfigurationProperties(prefix = "readybell.outbox")
public class OutboxProperties {

    private boolean enabled = true;
    /** How often the relay looks for due events (a commit also wakes it immediately). */
    private Duration pollInterval = Duration.ofSeconds(1);
    /** Events claimed per round. */
    private int batchSize = 20;
    /** A claimed event that is not finished within this time is claimed again (the worker probably died). */
    private Duration lease = Duration.ofMinutes(2);
    /** After this many failed attempts the event is parked as failed (metric {@code outbox.failed}). */
    private int maxAttempts = 10;
    private Duration initialBackoff = Duration.ofSeconds(2);
    private Duration maxBackoff = Duration.ofMinutes(5);
    /** Processed events are deleted after this time. */
    private Duration retention = Duration.ofDays(7);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public Duration getLease() { return lease; }
    public void setLease(Duration lease) { this.lease = lease; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Duration getInitialBackoff() { return initialBackoff; }
    public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = initialBackoff; }
    public Duration getMaxBackoff() { return maxBackoff; }
    public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff; }
    public Duration getRetention() { return retention; }
    public void setRetention(Duration retention) { this.retention = retention; }
}
