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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Database-backed staff accounts: a platform admin creates a restaurant with its first admin, the admin
 * creates staff, and everyone signs in with their own password and is bound to their own tenant.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffAccountAuthApiTest {

    @Autowired private MockMvc mvc;
    @MockBean private NotificationSender notificationSender;

    private static String uniq(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private ResultActions platformCreate(String body) throws Exception {
        return mvc.perform(post("/api/platform/restaurants").with(httpBasic("platform", "platform"))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String user, String pass) throws Exception {
        return mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}")
                .with(r -> { r.setRemoteAddr("10.1." + (Math.abs(user.hashCode()) % 250) + ".1"); return r; }));
    }

    private String tokenOf(String user, String pass) throws Exception {
        return JsonPath.read(login(user, pass).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
    }

    private String createRestaurant(String identifier, String adminUser, String adminPass) throws Exception {
        String body = platformCreate("{\"identifier\":\"" + identifier + "\",\"name\":\"R\",\"adminUsername\":\""
                + adminUser + "\",\"adminPassword\":\"" + adminPass + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.adminUsername").value(adminUser.strip().toLowerCase()))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.tenantId");
    }

    private ResultActions adminCreateStaff(String adminToken, String username, String role, String password) throws Exception {
        return mvc.perform(post("/api/admin/staff").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"role\":\"" + role + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void firstAdminCanSignInAndIsBoundToTheNewTenant() throws Exception {
        String admin = uniq("owner");
        String tenant = createRestaurant(uniq("cafe-"), admin, "s3cret-pass");

        login(admin, "s3cret-pass").andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RESTAURANT_ADMIN"))
                .andExpect(jsonPath("$.tenantId").value(tenant));
        login(admin, "wrong-pass").andExpect(status().isUnauthorized());
    }

    @Test
    void usernamesAreCaseInsensitiveAndNormalised() throws Exception {
        String name = uniq("Mixed");
        createRestaurant(uniq("cafe-"), "  " + name + "  ", "s3cret-pass");
        login(name.toUpperCase(), "s3cret-pass").andExpect(status().isOk());
        login(name.toLowerCase(), "s3cret-pass").andExpect(status().isOk());
    }

    @Test
    void adminCreatesStaffWhoSignsInAndSeesOnlyOwnTenant() throws Exception {
        String admin = uniq("owner");
        String tenant = createRestaurant(uniq("cafe-"), admin, "s3cret-pass");
        String adminToken = tokenOf(admin, "s3cret-pass");

        String staff = uniq("waiter");
        adminCreateStaff(adminToken, staff, "STAFF", "waiter-pass1").andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(staff))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        login(staff, "waiter-pass1").andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STAFF")).andExpect(jsonPath("$.tenantId").value(tenant));
        String staffToken = tokenOf(staff, "waiter-pass1");
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
        // the legacy header cannot move a user to another tenant
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + staffToken)
                        .header("X-Tenant-Id", "00000000-0000-0000-0000-0000000000a1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotManageAccounts_adminsCan() throws Exception {
        String admin = uniq("owner");
        createRestaurant(uniq("cafe-"), admin, "s3cret-pass");
        String adminToken = tokenOf(admin, "s3cret-pass");
        String staff = uniq("waiter");
        adminCreateStaff(adminToken, staff, "STAFF", "waiter-pass1").andExpect(status().isCreated());

        adminCreateStaff(tokenOf(staff, "waiter-pass1"), uniq("x"), "STAFF", "another-pass1")
                .andExpect(status().isForbidden());
        adminCreateStaff(adminToken, uniq("manager"), "RESTAURANT_ADMIN", "manager-pass1")
                .andExpect(status().isCreated());
    }

    @Test
    void usernamesAreUniqueAcrossTenants() throws Exception {
        String taken = uniq("shared");
        createRestaurant(uniq("a-"), taken, "s3cret-pass");

        // another restaurant cannot reuse it for its first admin ...
        platformCreate("{\"identifier\":\"" + uniq("b-") + "\",\"name\":\"R\",\"adminUsername\":\"" + taken.toUpperCase()
                + "\",\"adminPassword\":\"s3cret-pass\"}").andExpect(status().isConflict());
        // ... nor for a staff member
        String otherAdmin = uniq("owner");
        createRestaurant(uniq("c-"), otherAdmin, "s3cret-pass");
        adminCreateStaff(tokenOf(otherAdmin, "s3cret-pass"), taken, "STAFF", "waiter-pass1").andExpect(status().isConflict());
    }

    @Test
    void failedCreateWithAdminLeavesNoRestaurantBehind() throws Exception {
        String taken = uniq("shared");
        createRestaurant(uniq("a-"), taken, "s3cret-pass");
        String identifier = uniq("retry-");

        platformCreate("{\"identifier\":\"" + identifier + "\",\"name\":\"R\",\"adminUsername\":\"" + taken
                + "\",\"adminPassword\":\"s3cret-pass\"}").andExpect(status().isConflict());
        // the identifier is still free, so the retry with a different admin succeeds
        createRestaurant(identifier, uniq("owner"), "s3cret-pass");
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        String admin = uniq("owner");
        createRestaurant(uniq("cafe-"), admin, "s3cret-pass");
        String token = tokenOf(admin, "s3cret-pass");

        adminCreateStaff(token, uniq("w"), "STAFF", "short").andExpect(status().isBadRequest());           // password < 8
        adminCreateStaff(token, "ab", "STAFF", "long-enough1").andExpect(status().isBadRequest());         // username too short
        adminCreateStaff(token, "bad name!", "STAFF", "long-enough1").andExpect(status().isBadRequest());  // characters
        adminCreateStaff(token, uniq("w"), "KING", "long-enough1").andExpect(status().isBadRequest());     // unknown role
        platformCreate("{\"identifier\":\"" + uniq("x-") + "\",\"name\":\"R\",\"adminUsername\":\"" + uniq("o")
                + "\"}").andExpect(status().isBadRequest());                                               // username without password
        platformCreate("{\"identifier\":\"" + uniq("x-") + "\",\"name\":\"R\",\"adminUsername\":\"" + uniq("o")
                + "\",\"adminPassword\":\"short\"}").andExpect(status().isBadRequest());
    }

    @Test
    void deactivatedStaffCannotSignInOrRefresh() throws Exception {
        String admin = uniq("owner");
        createRestaurant(uniq("cafe-"), admin, "s3cret-pass");
        String adminToken = tokenOf(admin, "s3cret-pass");
        String staff = uniq("waiter");
        String accountId = JsonPath.read(adminCreateStaff(adminToken, staff, "STAFF", "waiter-pass1")
                .andReturn().getResponse().getContentAsString(), "$.accountId");
        String staffToken = tokenOf(staff, "waiter-pass1");

        mvc.perform(delete("/api/admin/staff/{id}", accountId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DEACTIVATED"));

        login(staff, "waiter-pass1").andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedRestaurantLocksItsUsersOut() throws Exception {
        String admin = uniq("owner");
        String tenant = createRestaurant(uniq("cafe-"), admin, "s3cret-pass");
        login(admin, "s3cret-pass").andExpect(status().isOk());

        mvc.perform(delete("/api/platform/restaurants/{id}", tenant).with(httpBasic("platform", "platform")))
                .andExpect(status().isOk());

        login(admin, "s3cret-pass").andExpect(status().isUnauthorized());
    }

    @Test
    void unknownUserIsRejected() throws Exception {
        login(uniq("ghost"), "whatever-pass").andExpect(status().isUnauthorized());
    }
}
