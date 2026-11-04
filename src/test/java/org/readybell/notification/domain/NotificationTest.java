package org.readybell.notification.domain;

import org.junit.jupiter.api.Test;
import org.readybell.shared.domain.TenantId;
import org.readybell.subscription.domain.DeviceSubscriptionId;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final Clock CLOCK = Clock.systemUTC();

    private static Notification pending() {
        return Notification.compose(TENANT, DeviceSubscriptionId.newId(), "Order ready");
    }

    @Test
    void startsPendingWithoutAnOrderUnlessGiven() {
        var plain = pending();
        assertThat(plain.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(plain.orderId()).isNull();

        var orderId = UUID.randomUUID();
        var forOrder = Notification.compose(TENANT, DeviceSubscriptionId.newId(), "x", orderId);
        assertThat(forOrder.orderId()).isEqualTo(orderId);
    }

    @Test
    void sentThenDeliveredRecordsAnEventEach() {
        var n = pending();

        n.markSent(CLOCK);
        assertThat(n.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.pendingEvents()).hasSize(1).first().isInstanceOf(NotificationSent.class);

        n.markDelivered(CLOCK);
        assertThat(n.status()).isEqualTo(NotificationStatus.DELIVERED);
        assertThat(n.pendingEvents()).hasSize(2).last().isInstanceOf(NotificationDelivered.class);
    }

    @Test
    void cannotBeSentTwice() {
        var n = pending();
        n.markSent(CLOCK);

        assertThatThrownBy(() -> n.markSent(CLOCK)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotBeDeliveredBeforeItIsSent() {
        assertThatThrownBy(() -> pending().markDelivered(CLOCK)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedFromPendingOrSentButNotAfterDelivery() {
        var fromPending = pending();
        fromPending.markFailed();
        assertThat(fromPending.status()).isEqualTo(NotificationStatus.FAILED);

        var fromSent = pending();
        fromSent.markSent(CLOCK);
        fromSent.markFailed();
        assertThat(fromSent.status()).isEqualTo(NotificationStatus.FAILED);

        var delivered = pending();
        delivered.markSent(CLOCK);
        delivered.markDelivered(CLOCK);
        assertThatThrownBy(delivered::markFailed).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullClockAndNullArgumentsAreRejected() {
        assertThatThrownBy(() -> pending().markSent(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> pending().markDelivered(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Notification.compose(null, DeviceSubscriptionId.newId(), "x"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Notification.compose(TENANT, null, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Notification.compose(TENANT, DeviceSubscriptionId.newId(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
