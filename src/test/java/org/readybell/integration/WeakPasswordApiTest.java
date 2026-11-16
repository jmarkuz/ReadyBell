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
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Passwords of restaurant admins and staff must be at least 10 characters and not guessable. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WeakPasswordApiTest {

    @Autowired private MockMvc mvc;
    @MockBean private NotificationSender notificationSender;

    private ResultActions createRestaurant(String suffix, String admin, String password) throws Exception {
        return mvc.perform(post("/api/platform/restaurants").with(httpBasic("platform", "platform"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"cafe-" + suffix + "\",\"name\":\"R\",\"adminUsername\":\"" + admin
                        + "\",\"adminPassword\":\"" + password + "\"}"));
    }

    @Test
    void aWeakRestaurantAdminPasswordIsRefusedWithAReason() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        createRestaurant(suffix, "owner" + suffix, "password123").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("That password is too common; choose another"));
        createRestaurant(suffix, "owner" + suffix, "short-pw9").andExpect(status().isBadRequest());
        createRestaurant(suffix, "owner" + suffix, "xx-owner" + suffix + "-1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Password must not contain the username"));
        createRestaurant(suffix, "owner" + suffix, "correct-horse-battery").andExpect(status().isCreated());
    }

    @Test
    void aWeakStaffPasswordIsRefused() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        createRestaurant(suffix, "owner" + suffix, "correct-horse-battery").andExpect(status().isCreated());
        String body = mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owner" + suffix + "\",\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");

        mvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"staff" + suffix + "\",\"role\":\"STAFF\",\"password\":\"qwertyuiop\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"staff" + suffix + "\",\"role\":\"STAFF\",\"password\":\"a-fine-passphrase-7\"}"))
                .andExpect(status().isCreated());
    }
}
