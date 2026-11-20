package org.readybell.notification.application;

/**
 * Tells whoever delivers pushes that new work is waiting, so the first attempt does not wait for the next scheduled
 * round. Must return at once: it is called on the thread that handles the {@code OrderReady} event.
 */
@FunctionalInterface
public interface DeliveryTrigger {

    void wake();
}
