package org.readybell.platform.auth;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.List;

/**
 * Optional: the platform operator (cross-tenant, the most valuable account) may sign in only from the listed networks
 * ({@code readybell.security.platform-admin-allowed-networks}, IPs or CIDRs; empty means anywhere). A sign-in from
 * elsewhere is answered exactly like a wrong password.
 */
public class OperatorNetworkPolicy {

    private final List<IpAddressMatcher> allowed;

    public OperatorNetworkPolicy(List<String> networks) {
        this.allowed = networks.stream().map(String::strip).filter(s -> !s.isEmpty()).map(IpAddressMatcher::new).toList();
    }

    public boolean isRestricted() {
        return !allowed.isEmpty();
    }

    public boolean allows(String address) {
        if (!isRestricted()) {
            return true;
        }
        for (IpAddressMatcher matcher : allowed) {
            try {
                if (matcher.matches(address)) {
                    return true;
                }
            } catch (IllegalArgumentException notAnAddress) {
                return false;
            }
        }
        return false;
    }
}
