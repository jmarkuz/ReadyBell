package org.readybell.notification.adapter.out.push;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.readybell.notification.domain.NotificationPayload;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.notification.domain.SendResult;
import org.readybell.subscription.domain.PushEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.Security;

/**
 * VAPID Web Push implementation of {@link NotificationSender}.
 *
 * <p>All VAPID credentials and web-push library details are confined to this adapter;
 * the domain sees only {@link NotificationSender} (Req 6.2, ADR-005).
 *
 * <p>HTTP outcome mapping:
 * <ul>
 *   <li>201 → {@link SendResult#ACCEPTED}</li>
 *   <li>404 / 410 → {@link SendResult#ENDPOINT_EXPIRED} (Req 3.6)</li>
 *   <li>any other status / exception → {@link SendResult#REJECTED} (Req 6.7, 6.8)</li>
 * </ul>
 *
 * <p>This bean is only created in non-test profiles. In test profiles, a
 * {@link org.readybell.notification.domain.FakeNotificationSender} is used instead
 * (via {@link IntegrationTestConfig}).
 */
@Component
@Profile("!test")
class VapidNotificationSenderAdapter implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(VapidNotificationSenderAdapter.class);

    private final PushService pushService;

    VapidNotificationSenderAdapter(
            @Value("${readybell.vapid.public-key}") String publicKey,
            @Value("${readybell.vapid.private-key}") String privateKey,
            @Value("${readybell.vapid.subject}") String subject) throws Exception {

        // BouncyCastle is required by the web-push library for EC key operations
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        this.pushService = new PushService(publicKey, privateKey, subject);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Builds a VAPID-signed Web Push message and dispatches it to the push service.
     * The JSON payload carries {@code title} and {@code body} fields so browsers can
     * display a standard notification without additional parsing (Req 6.2).
     */
    @Override
    public SendResult send(PushEndpoint endpoint, NotificationPayload payload) {
        try {
            String json = buildJson(payload);

            Notification notification = new Notification(
                    endpoint.endpointUrl(),
                    endpoint.p256dhKey(),
                    endpoint.authKey(),
                    json);

            HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();

            return mapStatusCode(statusCode);

        } catch (Exception e) {
            // Any I/O, security, or encoding exception is treated as a transient
            // delivery failure so processing can continue for other subscriptions (Req 6.7)
            log.warn("Web Push delivery failed for endpoint {}: {}",
                    endpoint.endpointUrl(), e.getMessage(), e);
            return SendResult.REJECTED;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String buildJson(NotificationPayload payload) {
        // Minimal JSON — avoids pulling in a serialisation library for a two-field object
        String escapedTitle = escapeJson(payload.title());
        String escapedBody  = escapeJson(payload.body());
        return "{\"title\":\"" + escapedTitle + "\",\"body\":\"" + escapedBody + "\"}";
    }

    /**
     * Escapes the characters that JSON requires to be escaped inside a string value.
     */
    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Maps a push-service HTTP status code to a {@link SendResult}.
     *
     * @param statusCode HTTP status returned by the push service
     * @return the corresponding {@link SendResult}
     */
    private static SendResult mapStatusCode(int statusCode) {
        return switch (statusCode) {
            case 201 -> SendResult.ACCEPTED;
            case 404, 410 -> SendResult.ENDPOINT_EXPIRED;
            default -> {
                log.warn("Unexpected push service HTTP status: {}", statusCode);
                yield SendResult.REJECTED;
            }
        };
    }
}
