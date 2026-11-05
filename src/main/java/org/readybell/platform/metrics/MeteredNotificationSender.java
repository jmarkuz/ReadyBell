package org.readybell.platform.metrics;

import org.readybell.notification.domain.NotificationPayload;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.notification.domain.SendResult;
import org.readybell.subscription.domain.PushEndpoint;

import java.util.Objects;

/** Decorates the real sender so every attempt is counted, whichever adapter is behind it. */
public class MeteredNotificationSender implements NotificationSender {

    private final NotificationSender delegate;
    private final PushMetrics metrics;

    public MeteredNotificationSender(NotificationSender delegate, PushMetrics metrics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Override
    public SendResult send(PushEndpoint endpoint, NotificationPayload payload) {
        SendResult result;
        try {
            result = delegate.send(endpoint, payload);
        } catch (RuntimeException e) {
            metrics.recordError();
            throw e;
        }
        metrics.recordResult(result);
        return result;
    }
}
