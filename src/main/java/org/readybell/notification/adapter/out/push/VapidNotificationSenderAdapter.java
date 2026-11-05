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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.Security;
import java.util.Optional;

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

    private final Optional<PushService> pushService;

    @org.springframework.beans.factory.annotation.Autowired
    VapidNotificationSenderAdapter(
            @Value("${readybell.vapid.public-key}") String publicKey,
            @Value("${readybell.vapid.private-key}") String privateKey,
            @Value("${readybell.vapid.subject}") String subject) {

        // Skip initialization if using placeholder keys (development/testing without real VAPID credentials)
        if (isPlaceholder(publicKey) || isPlaceholder(privateKey)) {
            log.warn("VAPID credentials are not configured. Web Push notifications will be rejected. " +
                    "Set VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY environment variables for production use.");
            this.pushService = Optional.empty();
        } else {
            this.pushService = initializePushService(publicKey, privateKey, subject);
        }
    }

    /** For tests: use the given push service (empty means credentials are not configured). */
    VapidNotificationSenderAdapter(Optional<PushService> pushService) {
        this.pushService = pushService;
    }

    private static Optional<PushService> initializePushService(String publicKey, String privateKey, String subject) {
        try {
            // BouncyCastle is required by the web-push library for EC key operations
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }

            PushService service = new PushService(publicKey, privateKey, subject);
            log.info("VAPID Web Push service initialized successfully.");
            return Optional.of(service);
        } catch (Exception e) {
            log.error("Failed to initialize VAPID Web Push service with provided credentials. " +
                    "Web Push notifications will be rejected.", e);
            return Optional.empty();
        }
    }

    private static boolean isPlaceholder(String key) {
        return key == null || key.isBlank() || 
               key.startsWith("placeholder-") || 
               key.equals("${VAPID_PUBLIC_KEY:placeholder-public-key}") ||
               key.equals("${VAPID_PRIVATE_KEY:placeholder-private-key}");
    }

    @Override
    public SendResult send(PushEndpoint endpoint, NotificationPayload payload) {
        // If VAPID service is not initialized, reject all sends
        if (pushService.isEmpty()) {
            log.debug("Rejecting Web Push send: VAPID service not initialized");
            return SendResult.REJECTED;
        }

        try {
            String json = buildJson(payload);

            // An "order ready" ping is useless after an hour; HIGH urgency wakes a sleeping device
            Notification notification = Notification.builder()
                    .endpoint(endpoint.endpointUrl())
                    .userPublicKey(endpoint.p256dhKey())
                    .userAuth(endpoint.authKey())
                    .payload(json)
                    .ttl(3600)
                    .urgency(nl.martijndwars.webpush.Urgency.HIGH)
                    .build();

            HttpResponse response = pushService.get().send(notification);
            int statusCode = response.getStatusLine().getStatusCode();

            return mapStatusCode(statusCode);

        } catch (Exception e) {
            // Any I/O, security, or encoding exception is treated as a transient
            // delivery failure so processing can continue for other subscriptions (Req 6.7)
            log.warn("Web Push delivery failed for endpoint {}: {}",
                    endpoint.endpointUrl(), e.getMessage(), e);
            return SendResult.TRANSIENT_FAILURE;
        }
    }

    private static String buildJson(NotificationPayload payload) {
        // Minimal JSON — avoids pulling in a serialisation library for a two-field object
        String escapedTitle = escapeJson(payload.title());
        String escapedBody  = escapeJson(payload.body());
        String url = payload.url() == null ? "" : ",\"url\":\"" + escapeJson(payload.url()) + "\"";
        return "{\"title\":\"" + escapedTitle + "\",\"body\":\"" + escapedBody + "\"" + url + "}";
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
            case 429, 500, 502, 503, 504 -> {
                log.warn("Push service temporarily unavailable (HTTP {})", statusCode);
                yield SendResult.TRANSIENT_FAILURE;
            }
            default -> {
                log.warn("Unexpected push service HTTP status: {}", statusCode);
                yield SendResult.REJECTED;
            }
        };
    }
}
