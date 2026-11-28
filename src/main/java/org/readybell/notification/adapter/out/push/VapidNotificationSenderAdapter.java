package org.readybell.notification.adapter.out.push;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.readybell.notification.domain.NotificationPayload;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.notification.domain.SendResult;
import org.readybell.subscription.domain.PushEndpoint;
import org.readybell.subscription.domain.PushEndpointPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.Security;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * VAPID Web Push implementation of {@link NotificationSender}.
 *
 * <p>All VAPID credentials and web-push library details are confined to this adapter;
 * the domain sees only {@link NotificationSender} (, ADR-005).
 *
 * <p>HTTP outcome mapping:
 * <ul>
 *   <li>201 → {@link SendResult#ACCEPTED}</li>
 *   <li>404 / 410 → {@link SendResult#ENDPOINT_EXPIRED}</li>
 *   <li>any other status / exception → {@link SendResult#REJECTED}</li>
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

    /** Default limit for one push request: a push service that does not answer in this time counts as unavailable. */
    static final Duration DEFAULT_SEND_TIMEOUT = Duration.ofSeconds(5);

    private final Optional<PushService> pushService;
    private final PushEndpointPolicy endpointPolicy;
    private final Duration sendTimeout;

    VapidNotificationSenderAdapter(String publicKey, String privateKey, String subject, PushEndpointPolicy endpointPolicy) {
        this(publicKey, privateKey, subject, endpointPolicy, DEFAULT_SEND_TIMEOUT);
    }

    @org.springframework.beans.factory.annotation.Autowired
    VapidNotificationSenderAdapter(
            @Value("${readybell.vapid.public-key}") String publicKey,
            @Value("${readybell.vapid.private-key}") String privateKey,
            @Value("${readybell.vapid.subject}") String subject,
            PushEndpointPolicy endpointPolicy,
            @Value("${readybell.push.send-timeout:5s}") Duration sendTimeout) {
        this.endpointPolicy = endpointPolicy;
        this.sendTimeout = sendTimeout;

        // Skip initialization if using placeholder keys (development/testing without real VAPID credentials)
        if (isPlaceholder(publicKey) || isPlaceholder(privateKey)) {
            log.warn("VAPID credentials are not configured. Web Push notifications will be rejected. " +
                    "Set VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY environment variables for production use.");
            this.pushService = Optional.empty();
        } else {
            this.pushService = initializePushService(publicKey, privateKey, subject);
        }
    }

    VapidNotificationSenderAdapter(Optional<PushService> pushService) {
        this(pushService, PushEndpointPolicy.defaults());
    }

    VapidNotificationSenderAdapter(Optional<PushService> pushService, PushEndpointPolicy endpointPolicy) {
        this(pushService, endpointPolicy, DEFAULT_SEND_TIMEOUT);
    }

    VapidNotificationSenderAdapter(Optional<PushService> pushService, PushEndpointPolicy endpointPolicy,
                                   Duration sendTimeout) {
        this.pushService = pushService;
        this.endpointPolicy = endpointPolicy;
        this.sendTimeout = sendTimeout;
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

        // Subscriptions stored before the policy existed (or edited in the database) are checked again at send time:
        if (!endpointPolicy.isAllowed(endpoint.endpointUrl())) {
            log.warn("Refusing to send Web Push: endpoint host {} is not an allowed push service",
                    PushEndpointPolicy.hostForLog(endpoint.endpointUrl()));
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

            // The library's blocking send() waits as long as the connection lasts; ask for the response asynchronously and
            // wait only for the timeout, so one stuck push service ties up a delivery worker for seconds, not minutes
            Future<HttpResponse> pending = pushService.get().sendAsync(notification);
            HttpResponse response;
            try {
                response = pending.get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                pending.cancel(true);
                log.warn("Web Push to endpoint host {} timed out after {} ms",
                        PushEndpointPolicy.hostForLog(endpoint.endpointUrl()), sendTimeout.toMillis());
                return SendResult.TRANSIENT_FAILURE;
            }
            int statusCode = response.getStatusLine().getStatusCode();

            return mapStatusCode(statusCode);
        } catch (Exception e) {
            // Any I/O, security, or encoding exception is treated as a transient
            // delivery failure so processing can continue for other subscriptions
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Web Push delivery failed for endpoint host {}: {}",
                    PushEndpointPolicy.hostForLog(endpoint.endpointUrl()), e.getMessage(), e);
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
