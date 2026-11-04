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

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Listing endpoints behind the admin screens: restaurants, own profile, staff. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminListingApiTest {

    @Autowired private MockMvc mvc;
    @MockBean private NotificationSender notificationSender;

    @Test
    void platformListsRestaurantsAndAdminSeesOwnProfileAndStaff() throws Exception {
        String id = "list-" + UUID.randomUUID().toString().substring(0, 8);
        String admin = "adm" + id.substring(5);
        mvc.perform(post("/api/platform/restaurants").with(httpBasic("platform", "platform"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + id + "\",\"name\":\"Listing Cafe\",\"adminUsername\":\"" + admin
                                + "\",\"adminPassword\":\"long-enough-pass\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/platform/restaurants").with(httpBasic("platform", "platform")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.identifier=='" + id + "')].name").value("Listing Cafe"))
                .andExpect(jsonPath("$[?(@.identifier=='" + id + "')].status").value("ACTIVE"));

        mvc.perform(get("/api/restaurant").with(httpBasic(admin, "long-enough-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Listing Cafe"))
                .andExpect(jsonPath("$.identifier").value(id));

        mvc.perform(post("/api/admin/staff").with(httpBasic(admin, "long-enough-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cashier" + id.substring(5) + "\",\"role\":\"STAFF\",\"password\":\"long-enough-pass\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/admin/staff").with(httpBasic(admin, "long-enough-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").exists());
    }

    @Test
    void listingIsRoleProtected() throws Exception {
        mvc.perform(get("/api/platform/restaurants").with(httpBasic("admin", "admin"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/platform/restaurants")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/staff").with(httpBasic("platform", "platform"))).andExpect(status().isForbidden());
    }
}
