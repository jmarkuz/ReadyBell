package org.readybell.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-out, refresh rotation and account removal take effect on the staff tokens. */
@SpringBootTest(properties = "readybell.security.account-check-cache=0s")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TokenLifecycleApiTest {

    @Autowired private MockMvc mvc;
    @MockBean private NotificationSender notificationSender;

    private String login(String user, String pass) throws Exception {
        String body = mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private void canListOrders(String token, int expected) throws Exception {
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token)).andExpect(status().is(expected));
    }

    @Test
    void signOutMakesACopiedTokenUseless() throws Exception {
        String token = login("admin", "admin");
        canListOrders(token, 200);

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());

        canListOrders(token, 401);
        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void signingOutOneChainLeavesAnotherSignInAlone() throws Exception {
        String laptop = login("admin", "admin");
        String tablet = login("admin", "admin");

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + laptop)).andExpect(status().isNoContent());

        canListOrders(laptop, 401);
        canListOrders(tablet, 200);
    }

    @Test
    void logoutIsHarmlessWithoutAValidToken() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer garbage")).andExpect(status().isNoContent());
    }

    @Test
    void aRefreshedTokenKeepsTheChainAndTheOldOneWorksForTheOverlap() throws Exception {
        String old = login("admin", "admin");
        String body = mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + old))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String refreshed = JsonPath.read(body, "$.token");

        canListOrders(old, 200);            // requests already in flight with the old token still succeed
        canListOrders(refreshed, 200);

        // signing out with the new token ends the whole chain, the old one included
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + refreshed)).andExpect(status().isNoContent());
        canListOrders(old, 401);
        canListOrders(refreshed, 401);
    }

    @Test
    void aDeletedStaffMemberLosesAccessAtOnce() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String owner = "owner" + suffix;
        mvc.perform(post("/api/platform/restaurants").with(httpBasic("platform", "platform"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"cafe-" + suffix + "\",\"name\":\"R\",\"adminUsername\":\"" + owner
                                + "\",\"adminPassword\":\"s3cret-pass\"}"))
                .andExpect(status().isCreated());
        String admin = login(owner, "s3cret-pass");
        String username = "staff" + suffix;
        String created = mvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"a-good-password-1\",\"role\":\"STAFF\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.accountId");
        String staffToken = login(username, "a-good-password-1");
        canListOrders(staffToken, 200);

        mvc.perform(delete("/api/admin/staff/" + id).header("Authorization", "Bearer " + admin)).andExpect(status().isOk());

        canListOrders(staffToken, 401);     // not "until the token expires"
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"a-good-password-1\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }
}
