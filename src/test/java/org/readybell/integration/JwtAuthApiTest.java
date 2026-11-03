package org.readybell.integration;

import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.platform.TenantUser;
import org.readybell.platform.auth.JwtProperties;
import org.readybell.platform.auth.JwtService;
import org.readybell.shared.domain.TenantId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login, Bearer authentication and token refresh against the dashboard contract. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtAuthApiTest {

    private static final String TENANT_A = "00000000-0000-0000-0000-0000000000a1";
    private static final String TENANT_B = "00000000-0000-0000-0000-0000000000b2";

    @Autowired private MockMvc mvc;
    @Autowired private JwtProperties props;
    @MockBean private NotificationSender notificationSender;

    private String login(String user, String pass) throws Exception {
        String body = mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    /** Token signed with the real key but issued {@code ago} in the past. */
    private String tokenIssuedAgo(Duration ago) {
        Clock past = Clock.fixed(Instant.now().minus(ago), ZoneOffset.UTC);
        var user = new TenantUser("admin", "", List.of(new SimpleGrantedAuthority("ROLE_RESTAURANT_ADMIN")),
                new TenantId(UUID.fromString(TENANT_A)));
        return new JwtService(props, past).issue(user).token();
    }

    @Test
    void loginReturnsDashboardContract() throws Exception {
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("RESTAURANT_ADMIN"))
                .andExpect(jsonPath("$.tenantId").value(TENANT_A))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void platformAdminHasNoTenant() throws Exception {
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"platform\",\"password\":\"platform\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PLATFORM_ADMIN"))
                .andExpect(jsonPath("$.tenantId").doesNotExist());
    }

    @Test
    void badCredentialsAreRejectedForKnownAndUnknownUsers() throws Exception {
        for (String body : List.of("{\"username\":\"admin\",\"password\":\"x\"}",
                                   "{\"username\":\"ghost\",\"password\":\"x\"}")) {
            mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("urn:readybell:auth:invalid-credentials"));
        }
    }

    @Test
    void blankLoginBodyIsBadRequest() throws Exception {
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bearerTokenAuthenticatesAndBindsTenant() throws Exception {
        String token = login("admin", "admin");
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // tenant is fixed by the token: a different tenant in the legacy header is refused
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token).header("X-Tenant-Id", TENANT_B))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformTokenReachesPlatformApiButStaffTokenDoesNot() throws Exception {
        mvc.perform(post("/api/platform/restaurants").header("Authorization", "Bearer " + login("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        int s = mvc.perform(post("/api/platform/restaurants").header("Authorization", "Bearer " + login("platform", "platform"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus();
        assertThat(s).isNotIn(401, 403);
    }

    @Test
    void tamperedAndExpiredTokensAreRejected() throws Exception {
        String token = login("admin", "admin");
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + tampered)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + tokenIssuedAgo(Duration.ofHours(2))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenCanBeRefreshedWithinGrace() throws Exception {
        String expired = tokenIssuedAgo(Duration.ofHours(2));
        String body = mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + expired))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String fresh = body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + fresh)).andExpect(status().isOk());
    }

    @Test
    void sseStreamAcceptsTokenQueryParameterButOtherEndpointsDoNot() throws Exception {
        String token = login("admin", "admin");
        // the stream starts (async), i.e. it is authenticated
        mvc.perform(get("/api/orders/stream").param("token", token))
                .andExpect(request().asyncStarted());
        mvc.perform(get("/api/orders/stream").param("token", "garbage")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders/stream")).andExpect(status().isUnauthorized());
        // query-parameter tokens must not work anywhere else
        mvc.perform(get("/api/orders").param("token", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOlderThanGraceCannotBeRefreshed() throws Exception {
        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + tokenIssuedAgo(Duration.ofDays(30))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRequiresBearerAndRejectsGarbage() throws Exception {
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer not.a.jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshForRemovedUserIsRejected() throws Exception {
        var ghost = new TenantUser("ghost", "", List.of(new SimpleGrantedAuthority("ROLE_STAFF")),
                new TenantId(UUID.fromString(TENANT_A)));
        String token = new JwtService(props, Clock.systemUTC()).issue(ghost).token();
        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
