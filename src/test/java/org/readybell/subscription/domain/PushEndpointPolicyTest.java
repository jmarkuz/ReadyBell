package org.readybell.subscription.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PushEndpointPolicyTest {

    private final PushEndpointPolicy policy = PushEndpointPolicy.defaults();

    @ParameterizedTest
    @ValueSource(strings = {
            "https://fcm.googleapis.com/fcm/send/abc",
            "https://updates.push.services.mozilla.com/wpush/v2/abc",
            "https://web.push.apple.com/QGxyz",
            "https://eu-west.push.apple.com/3/device/abc",
            "https://db5p.notify.windows.com/?token=abc",
            "https://FCM.GoogleAPIs.com:443/fcm/send/abc",       // case and the explicit default port
    })
    void realPushServicesAreAccepted(String url) {
        policy.validateEndpoint(url);
        assertThat(policy.isAllowed(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://fcm.googleapis.com/fcm/send/abc",             // not https
            "https://localhost/x",
            "https://127.0.0.1/x",
            "https://10.0.0.5/x",
            "https://169.254.169.254/latest/meta-data/",          // cloud metadata
            "https://[::1]/x",
            "https://2130706433/x",                               // 127.0.0.1 as one number
            "https://0x7f000001/x",
            "https://internal-service/x",                         // single-label internal name
            "https://evil.example/x",
            "https://fcm.googleapis.com.evil.example/x",          // allowed name as a prefix of another domain
            "https://evilfcm.googleapis.com.example/x",
            "https://notfcm.googleapis.com/x",                    // exact-host entry must not match a sibling
            "https://push.apple.com/x",                           // "*.push.apple.com" needs a subdomain
            "https://user:pass@fcm.googleapis.com/x",             // credentials in the URL
            "https://fcm.googleapis.com:8443/x",                  // unusual port
            "ftp://fcm.googleapis.com/x",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "//fcm.googleapis.com/x",
            "https:///x",
            "not a url",
            "",
    })
    void everythingElseIsRejected(String url) {
        assertThatThrownBy(() -> policy.validateEndpoint(url)).isInstanceOf(InvalidPushEndpointException.class);
        assertThat(policy.isAllowed(url)).isFalse();
    }

    @Test
    void anOverlongEndpointOrKeyIsRejected() {
        String longPath = "https://fcm.googleapis.com/" + "a".repeat(PushEndpointPolicy.MAX_ENDPOINT_LENGTH);
        assertThatThrownBy(() -> policy.validate(longPath, "k", "a")).hasMessageContaining("too long");
        assertThatThrownBy(() -> policy.validate("https://fcm.googleapis.com/x", "k".repeat(300), "a"))
                .hasMessageContaining("p256dhKey");
        assertThatThrownBy(() -> policy.validate("https://fcm.googleapis.com/x", "k", "a".repeat(300)))
                .hasMessageContaining("authKey");
    }

    @Test
    void extraHostsExtendTheListWithoutReplacingIt() {
        var custom = new PushEndpointPolicy(List.of("push.mycompany.example", " *.edge.mycompany.example "));

        assertThat(custom.isAllowed("https://push.mycompany.example/x")).isTrue();
        assertThat(custom.isAllowed("https://eu.edge.mycompany.example/x")).isTrue();
        assertThat(custom.isAllowed("https://fcm.googleapis.com/x")).isTrue();      // defaults stay
        assertThat(custom.isAllowed("https://mycompany.example/x")).isFalse();
    }

    @Test
    void logsShowTheHostNeverThePathThatActsAsASecret() {
        assertThat(PushEndpointPolicy.hostForLog("https://fcm.googleapis.com/fcm/send/SECRET-TOKEN")).isEqualTo("fcm.googleapis.com");
        assertThat(PushEndpointPolicy.hostForLog("::: not a url")).doesNotContain("not a url");
    }
}
