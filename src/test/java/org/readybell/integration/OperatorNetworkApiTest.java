package org.readybell.integration;

import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.testsupport.TestRestaurantSeeder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With allowed networks set, the platform operator can sign in only from them; everyone else is unaffected. */
@SpringBootTest(properties = "readybell.security.platform-admin-allowed-networks=192.0.2.0/24")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperatorNetworkApiTest {

    @Autowired private MockMvc mvc;
    @MockBean private NotificationSender notificationSender;

    private ResultActions login(String user, String pass, String address) throws Exception {
        return mvc.perform(post("/api/login").with(r -> { r.setRemoteAddr(address); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"restaurant\":\"" + TestRestaurantSeeder.restaurantFor(user) + "\",\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}"));
    }

    @Test
    void theOperatorSignsInFromAnAllowedNetworkOnly() throws Exception {
        login("platform", "platform", "192.0.2.55").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("PLATFORM_ADMIN"));
        // right password, wrong network: indistinguishable from a wrong password
        login("platform", "platform", "203.0.113.9").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void restaurantStaffAreNotRestricted() throws Exception {
        login("admin", "admin", "203.0.113.9").andExpect(status().isOk());
    }

    @Test
    void anOperatorTokenCannotBeRefreshedFromOutside() throws Exception {
        String body = login("platform", "platform", "192.0.2.56").andReturn().getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(body, "$.token");

        mvc.perform(post("/api/auth/refresh").with(r -> { r.setRemoteAddr("192.0.2.56"); return r; })
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh").with(r -> { r.setRemoteAddr("203.0.113.9"); return r; })
                .header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }
}
