package org.readybell.notification.domain;

import org.readybell.subscription.domain.PushEndpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory test fake for {@link NotificationSender}.
 *
 * <p>Behaviour is configurable per endpoint URL via {@link #stubResult(String, SendResult)}.
 * Any endpoint that has not been explicitly stubbed returns {@link SendResult#ACCEPTED}
 * (the happy-path default).
 *
 * <p>Interaction inspection is available through:
 * <ul>
 *   <li>{@link #getSentCount()} — total number of {@link #send} calls</li>
 *   <li>{@link #getLastPayload()} — payload from the most recent send call</li>
 *   <li>{@link #getLastEndpoint()} — endpoint from the most recent send call</li>
 *   <li>{@link #getSentPayloads()} — all payloads in call order (unmodifiable)</li>
 * </ul>
 *
 * <p>This class is <em>not</em> thread-safe; it is designed for single-threaded unit tests
 * and property tests.
 */
public class FakeNotificationSender implements NotificationSender {

    /** Per-endpoint URL override table. Missing entries fall back to {@link SendResult#ACCEPTED}. */
    private final Map<String, SendResult> stubResults = new HashMap<>();

    /** Ordered record of every payload passed to {@link #send}. */
    private final List<NotificationPayload> sentPayloads = new ArrayList<>();

    /** Ordered record of every endpoint passed to {@link #send}. */
    private final List<PushEndpoint> sentEndpoints = new ArrayList<>();

    // -------------------------------------------------------------------------
    // NotificationSender implementation
    // -------------------------------------------------------------------------

    @Override
    public SendResult send(PushEndpoint endpoint, NotificationPayload payload) {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(payload, "payload must not be null");

        sentEndpoints.add(endpoint);
        sentPayloads.add(payload);

        return stubResults.getOrDefault(endpoint.endpointUrl(), SendResult.ACCEPTED);
    }

    // -------------------------------------------------------------------------
    // Stub configuration
    // -------------------------------------------------------------------------

    /**
     * Configures the result that will be returned when {@link #send} is called with an
     * endpoint whose URL equals {@code endpointUrl}.
     *
     * @param endpointUrl the endpoint URL to stub (must not be {@code null})
     * @param result      the result to return for that URL (must not be {@code null})
     */
    public void stubResult(String endpointUrl, SendResult result) {
        Objects.requireNonNull(endpointUrl, "endpointUrl must not be null");
        Objects.requireNonNull(result, "result must not be null");
        stubResults.put(endpointUrl, result);
    }

    /**
     * Resets all stubs and clears all recorded interactions.
     */
    public void reset() {
        stubResults.clear();
        sentPayloads.clear();
        sentEndpoints.clear();
    }

    // -------------------------------------------------------------------------
    // Interaction inspection
    // -------------------------------------------------------------------------

    /**
     * Returns the total number of times {@link #send} was called.
     */
    public int getSentCount() {
        return sentPayloads.size();
    }

    /**
     * Returns the {@link NotificationPayload} from the most recent {@link #send} call, or
     * {@code null} if {@link #send} has not been called yet.
     */
    public NotificationPayload getLastPayload() {
        if (sentPayloads.isEmpty()) {
            return null;
        }
        return sentPayloads.get(sentPayloads.size() - 1);
    }

    /**
     * Returns the {@link PushEndpoint} from the most recent {@link #send} call, or
     * {@code null} if {@link #send} has not been called yet.
     */
    public PushEndpoint getLastEndpoint() {
        if (sentEndpoints.isEmpty()) {
            return null;
        }
        return sentEndpoints.get(sentEndpoints.size() - 1);
    }

    /**
     * Returns an unmodifiable view of all payloads passed to {@link #send}, in call order.
     */
    public List<NotificationPayload> getSentPayloads() {
        return Collections.unmodifiableList(sentPayloads);
    }

    /**
     * Returns an unmodifiable view of all endpoints passed to {@link #send}, in call order.
     */
    public List<PushEndpoint> getSentEndpoints() {
        return Collections.unmodifiableList(sentEndpoints);
    }
}
