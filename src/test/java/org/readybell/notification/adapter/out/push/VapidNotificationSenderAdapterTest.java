package org.readybell.notification.adapter.out.push;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.readybell.notification.domain.NotificationPayload;
import org.readybell.notification.domain.SendResult;
import org.readybell.subscription.domain.PushEndpoint;
import org.readybell.subscription.domain.PushEndpointPolicy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VapidNotificationSenderAdapterTest {

    private static PushEndpoint endpoint;
    private static final NotificationPayload PAYLOAD =
            new NotificationPayload("Café Aurora", "Order #AB12CD34 is ready", "/track/abc");

    @BeforeAll
    static void validDeviceKeys() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        var generator = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var publicKey = (org.bouncycastle.jce.interfaces.ECPublicKey) generator.generateKeyPair().getPublic();
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        endpoint = new PushEndpoint("https://fcm.googleapis.com/fcm/send/device",
                Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode(publicKey)),
                Base64.getUrlEncoder().withoutPadding().encodeToString(auth));
    }

    private static HttpResponse responseWith(int status) {
        var line = mock(StatusLine.class);
        when(line.getStatusCode()).thenReturn(status);
        var response = mock(HttpResponse.class);
        when(response.getStatusLine()).thenReturn(line);
        return response;
    }

    @ParameterizedTest(name = "HTTP {0} -> {1}")
    @CsvSource({
            "201, ACCEPTED",
            "404, ENDPOINT_EXPIRED",
            "410, ENDPOINT_EXPIRED",
            "429, TRANSIENT_FAILURE",
            "500, TRANSIENT_FAILURE",
            "502, TRANSIENT_FAILURE",
            "503, TRANSIENT_FAILURE",
            "504, TRANSIENT_FAILURE",
            "400, REJECTED",
            "401, REJECTED",
            "413, REJECTED",
            "200, REJECTED"})
    void mapsThePushServiceStatus(int status, SendResult expected) throws Exception {
        var service = mock(PushService.class);
        var response = responseWith(status);
        when(service.sendAsync(any(Notification.class))).thenReturn(CompletableFuture.completedFuture(response));

        assertThat(new VapidNotificationSenderAdapter(Optional.of(service)).send(endpoint, PAYLOAD))
                .isEqualTo(expected);
    }

    @Test
    void aPushServiceThatDoesNotAnswerInTimeIsATransientFailureAndTheRequestIsCancelled() throws Exception {
        var service = mock(PushService.class);
        var never = new CompletableFuture<org.apache.http.HttpResponse>();      // the connection hangs
        when(service.sendAsync(any(Notification.class))).thenReturn(never);
        var adapter = new VapidNotificationSenderAdapter(Optional.of(service), PushEndpointPolicy.defaults(),
                java.time.Duration.ofMillis(100));

        long started = System.nanoTime();
        assertThat(adapter.send(endpoint, PAYLOAD)).isEqualTo(SendResult.TRANSIENT_FAILURE);

        assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(3));
        assertThat(never.isCancelled()).isTrue();
    }

    @Test
    void withoutCredentialsEverythingIsRejected() {
        assertThat(new VapidNotificationSenderAdapter(Optional.empty()).send(endpoint, PAYLOAD))
                .isEqualTo(SendResult.REJECTED);
    }

    @Test
    void placeholderOrBlankKeysDisableThePushService() {
        var placeholder = new VapidNotificationSenderAdapter("placeholder-public-key", "placeholder-private-key", "mailto:a@b.c", PushEndpointPolicy.defaults());
        var blank = new VapidNotificationSenderAdapter(" ", "key", "mailto:a@b.c", PushEndpointPolicy.defaults());
        var unresolved = new VapidNotificationSenderAdapter("${VAPID_PUBLIC_KEY:placeholder-public-key}",
                "${VAPID_PRIVATE_KEY:placeholder-private-key}", "mailto:a@b.c", PushEndpointPolicy.defaults());

        assertThat(placeholder.send(endpoint, PAYLOAD)).isEqualTo(SendResult.REJECTED);
        assertThat(blank.send(endpoint, PAYLOAD)).isEqualTo(SendResult.REJECTED);
        assertThat(unresolved.send(endpoint, PAYLOAD)).isEqualTo(SendResult.REJECTED);
    }

    @Test
    void unusableCredentialsDoNotBreakStartupAndRejectSends() {
        var adapter = new VapidNotificationSenderAdapter("not-a-key", "not-a-key", "mailto:a@b.c", PushEndpointPolicy.defaults());

        assertThat(adapter.send(endpoint, PAYLOAD)).isEqualTo(SendResult.REJECTED);
    }

    @Test
    void networkErrorIsATransientFailure() throws Exception {
        var service = mock(PushService.class);
        when(service.sendAsync(any(Notification.class))).thenThrow(new java.io.IOException("connection reset"));

        assertThat(new VapidNotificationSenderAdapter(Optional.of(service)).send(endpoint, PAYLOAD))
                .isEqualTo(SendResult.TRANSIENT_FAILURE);
    }

    @Test
    void invalidDeviceKeysAreATransientFailureAndNothingIsSent() throws Exception {
        var service = mock(PushService.class);
        var broken = new PushEndpoint("https://fcm.googleapis.com/fcm/send/device", "AAAA", "AAAA");

        assertThat(new VapidNotificationSenderAdapter(Optional.of(service)).send(broken, PAYLOAD))
                .isEqualTo(SendResult.TRANSIENT_FAILURE);
        verify(service, never()).sendAsync(any(Notification.class));
    }

    @Test
    void payloadIsEscapedJsonWithTheTrackingUrl() throws Exception {
        var service = mock(PushService.class);
        var created = responseWith(201);
        when(service.sendAsync(any(Notification.class))).thenReturn(CompletableFuture.completedFuture(created));
        var tricky = new NotificationPayload("Joe's \"Diner\"", "line1\nline2\ttab \\ back", "/track/x");

        new VapidNotificationSenderAdapter(Optional.of(service)).send(endpoint, tricky);

        var captor = ArgumentCaptor.forClass(Notification.class);
        verify(service).sendAsync(captor.capture());
        String json = new String(captor.getValue().getPayload(), StandardCharsets.UTF_8);
        assertThat(json).isEqualTo(
                "{\"title\":\"Joe's \\\"Diner\\\"\",\"body\":\"line1\\nline2\\ttab \\\\ back\",\"url\":\"/track/x\"}");
        assertThat(captor.getValue().getTTL()).isEqualTo(3600);
    }

    @Test
    void payloadWithoutUrlOmitsTheField() throws Exception {
        var service = mock(PushService.class);
        var created = responseWith(201);
        when(service.sendAsync(any(Notification.class))).thenReturn(CompletableFuture.completedFuture(created));

        new VapidNotificationSenderAdapter(Optional.of(service))
                .send(endpoint, new NotificationPayload("Title", "Body", null));

        var captor = ArgumentCaptor.forClass(Notification.class);
        verify(service).sendAsync(captor.capture());
        assertThat(new String(captor.getValue().getPayload(), StandardCharsets.UTF_8))
                .isEqualTo("{\"title\":\"Title\",\"body\":\"Body\"}");
    }

    @Test
    void anEndpointOutsideThePushServicesIsNeverCalled() throws Exception {
        var service = mock(PushService.class);
        var internal = new PushEndpoint("http://169.254.169.254/latest/meta-data/", "p256dh", "auth");

        assertThat(new VapidNotificationSenderAdapter(Optional.of(service)).send(internal, PAYLOAD))
                .isEqualTo(SendResult.REJECTED);
        org.mockito.Mockito.verifyNoInteractions(service);
    }
}
