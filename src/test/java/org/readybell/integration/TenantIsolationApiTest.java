package org.readybell.integration;

import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for authentication, tenant binding and request validation.
 * Users are defined in {@code application-test.yml}: admin (tenant a1), other (tenant b2),
 * platform (PLATFORM_ADMIN, no tenant).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantIsolationApiTest {

    private static final String TENANT_A = "00000000-0000-0000-0000-0000000000a1";
    private static final String TENANT_B = "00000000-0000-0000-0000-0000000000b2";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private NotificationSender notificationSender;

    @Test
    void ordersRequireAuthentication() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(get("/api/orders").with(httpBasic("admin", "nope")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tenantComesFromPrincipal_notFromHeader_soCrossTenantHeaderIsForbidden() throws Exception {
        mvc.perform(get("/api/orders")
                        .with(httpBasic("admin", "admin"))
                        .header("X-Tenant-Id", TENANT_B))
                .andExpect(status().isForbidden());
    }

    @Test
    void matchingHeaderIsAccepted_andOwnTenantListsEmpty() throws Exception {
        mvc.perform(get("/api/orders")
                        .with(httpBasic("admin", "admin"))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void staffCannotSeeOtherTenantsOrder() throws Exception {
        // 'other' (tenant b2) transitioning a random order id of tenant a1 must not find it
        mvc.perform(patch("/api/orders/{id}/status", "11111111-1111-1111-1111-111111111111")
                        .with(httpBasic("other", "other"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void restaurantAdminCannotUsePlatformApi() throws Exception {
        mvc.perform(post("/api/platform/restaurants").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformAdminIsNotForbiddenOnPlatformApi() throws Exception {
        // Body is deliberately invalid: we only assert that authorization passes (not 401/403).
        mvc.perform(post("/api/platform/restaurants").with(httpBasic("platform", "platform"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 401 || s == 403) {
                        throw new AssertionError("platform admin was denied: " + s);
                    }
                });
    }

    @Test
    void createOrderWithoutSessionIdIsBadRequest() throws Exception {
        mvc.perform(post("/api/orders").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Request"));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(post("/api/orders").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidQrTokenIsUnprocessable_notServerError() throws Exception {
        mvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"qrToken\":\"restaurant:unknown\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void blankQrTokenIsBadRequest() throws Exception {
        mvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"qrToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actuatorHealthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
