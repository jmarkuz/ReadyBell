package org.readybell.platform.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static MockHttpServletRequest request(String peer, String forwardedFor) {
        var r = new MockHttpServletRequest();
        r.setRemoteAddr(peer);
        if (forwardedFor != null) r.addHeader("X-Forwarded-For", forwardedFor);
        return r;
    }

    @Test
    void ignoresForwardedHeaderWhenNoProxyIsTrusted() {
        var resolver = new ClientIpResolver(List.of());
        assertThat(resolver.resolve(request("198.51.100.7", "1.2.3.4"))).isEqualTo("198.51.100.7");
    }

    @Test
    void ignoresForwardedHeaderFromAnUntrustedPeer() {
        var resolver = new ClientIpResolver(List.of("10.0.0.0/8"));
        assertThat(resolver.resolve(request("198.51.100.7", "1.2.3.4"))).isEqualTo("198.51.100.7");
    }

    @Test
    void usesClientBehindATrustedProxy() {
        var resolver = new ClientIpResolver(List.of("10.0.0.0/8"));
        assertThat(resolver.resolve(request("10.1.2.3", "203.0.113.9"))).isEqualTo("203.0.113.9");
    }

    @Test
    void skipsTrustedHopsFromTheRightAndIgnoresSpoofedLeftEntries() {
        var resolver = new ClientIpResolver(List.of("10.0.0.0/8"));
        assertThat(resolver.resolve(request("10.1.2.3", "6.6.6.6, 203.0.113.9, 10.9.9.9")))
                .isEqualTo("203.0.113.9");
    }

    @Test
    void fallsBackToThePeerWithoutAHeaderOrWithGarbage() {
        var resolver = new ClientIpResolver(List.of("10.0.0.0/8"));
        assertThat(resolver.resolve(request("10.1.2.3", null))).isEqualTo("10.1.2.3");
        assertThat(resolver.resolve(request("10.1.2.3", "10.5.5.5"))).isEqualTo("10.1.2.3");
    }
}
