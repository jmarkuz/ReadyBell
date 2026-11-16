package org.readybell.notification.adapter.out.push;

import com.sun.net.httpserver.HttpServer;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The libraries behind Web Push (BouncyCastle, jose4j, the HTTP client) were upgraded for security fixes, and the adapter
 * tests mock the push service, so this sends one real, encrypted, VAPID-signed message to a local server standing in for a
 * push service. It proves the whole cryptographic stack still works, with no network access.
 */
class WebPushCryptoStackTest {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private static KeyPair p256() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    /** The uncompressed point (0x04 || X || Y) Web Push uses for public keys. */
    private static String publicKey(KeyPair pair) {
        var point = ((ECPublicKey) pair.getPublic()).getW();
        byte[] out = new byte[65];
        out[0] = 4;
        copy(point.getAffineX().toByteArray(), out, 1);
        copy(point.getAffineY().toByteArray(), out, 33);
        return B64.encodeToString(out);
    }

    private static String privateKey(KeyPair pair) {
        byte[] out = new byte[32];
        copy(((ECPrivateKey) pair.getPrivate()).getS().toByteArray(), out, 0);
        return B64.encodeToString(out);
    }

    /** Right-aligns a big-endian integer into 32 bytes at {@code offset}, dropping a sign byte. */
    private static void copy(byte[] big, byte[] out, int offset) {
        int length = Math.min(big.length, 32);
        System.arraycopy(big, big.length - length, out, offset + 32 - length, length);
    }

    @Test
    void anEncryptedVapidSignedMessageReachesThePushServiceInTheExpectedShape() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var authorization = new AtomicReference<String>();
        var encoding = new AtomicReference<String>();
        var cryptoKey = new AtomicReference<String>();
        var body = new AtomicReference<byte[]>();
        server.createContext("/push", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            encoding.set(exchange.getRequestHeaders().getFirst("Content-Encoding"));
            cryptoKey.set(exchange.getRequestHeaders().getFirst("Crypto-Key"));
            body.set(exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.start();
        try {
            var vapid = p256();
            var browser = p256();
            byte[] authSecret = new byte[16];
            new SecureRandom().nextBytes(authSecret);
            var service = new PushService(publicKey(vapid), privateKey(vapid), "mailto:ops@readybell.example");
            var notification = new Notification("http://127.0.0.1:" + server.getAddress().getPort() + "/push",
                    publicKey(browser), B64.encodeToString(authSecret),
                    "{\"title\":\"Order #12 is ready\"}".getBytes(StandardCharsets.UTF_8), 3600);

            var response = service.send(notification);

            assertThat(response.getStatusLine().getStatusCode()).isEqualTo(201);
            // the library's default encoding: "Authorization: WebPush <JWT>" plus the server's public key in Crypto-Key
            assertThat(authorization.get()).startsWith("WebPush ");
            assertThat(cryptoKey.get()).contains("p256ecdsa=").contains("dh=");
            assertThat(encoding.get()).isEqualTo("aesgcm");
            String[] jwt = authorization.get().substring("WebPush ".length()).split("\\.");
            assertThat(jwt).hasSize(3);                                                     // a signed JWT (ES256)
            String claims = new String(Base64.getUrlDecoder().decode(jwt[1]), StandardCharsets.UTF_8);
            assertThat(claims).contains("\"aud\":\"http://127.0.0.1\"").contains("\"sub\":\"mailto:ops@readybell.example\"").contains("\"exp\"");
            assertThat(body.get()).isNotEmpty();
            assertThat(new String(body.get(), StandardCharsets.ISO_8859_1)).doesNotContain("Order #12");   // encrypted, not plain
        } finally {
            server.stop(0);
        }
    }
}
