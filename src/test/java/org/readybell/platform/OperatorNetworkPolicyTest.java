package org.readybell.platform;

import org.junit.jupiter.api.Test;
import org.readybell.platform.auth.OperatorNetworkPolicy;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorNetworkPolicyTest {

    @Test
    void withoutNetworksTheOperatorMaySignInFromAnywhere() {
        var policy = new OperatorNetworkPolicy(List.of());

        assertThat(policy.isRestricted()).isFalse();
        assertThat(policy.allows("203.0.113.9")).isTrue();
    }

    @Test
    void withNetworksOnlyThoseAreAllowed() {
        var policy = new OperatorNetworkPolicy(List.of("192.0.2.10", " 198.51.100.0/24 ", ""));

        assertThat(policy.isRestricted()).isTrue();
        assertThat(policy.allows("192.0.2.10")).isTrue();
        assertThat(policy.allows("198.51.100.77")).isTrue();
        assertThat(policy.allows("198.51.101.1")).isFalse();
        assertThat(policy.allows("not an address")).isFalse();
    }
}
