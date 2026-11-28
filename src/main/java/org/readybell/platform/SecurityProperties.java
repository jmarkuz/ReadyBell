package org.readybell.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Externalised API users ({@code readybell.security.users}) for the Basic-auth MVP.
 *
 * <p>No users are defined by default; each environment supplies its own (see
 * {@code application-dev.yml}). Passwords are BCrypt-encoded at startup.
 */
@ConfigurationProperties(prefix = "readybell.security")
public class SecurityProperties {
    private List<User> users = new ArrayList<>();

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    public static class User {
        private String username;
        private String password;
        private List<String> roles = new ArrayList<>();
        private UUID tenantId;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
        public UUID getTenantId() { return tenantId; }
        public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    }
}
