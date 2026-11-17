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

import static org.assertj.core.api.Assertions.assertThat;
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

    /** Sign-in names the restaurant: usernames are unique only within one. */
    private ResultActions login(String restaurant, String user, String pass) throws Exception {
        return mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"restaurant\":\"" + restaurant + "\",\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}")
                .with(r -> { r.setRemoteAddr("10.1." + (Math.abs((restaurant + user).hashCode()) % 250) + ".1"); return r; }));
    }

    private String tokenOf(String restaurant, String user, String pass) throws Exception {
        return JsonPath.read(login(restaurant, user, pass).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
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
        String cafe = uniq("cafe-");
        String tenant = createRestaurant(cafe, admin, "s3cret-pass");

        login(cafe, admin, "s3cret-pass").andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RESTAURANT_ADMIN"))
                .andExpect(jsonPath("$.tenantId").value(tenant));
        login(cafe, admin, "wrong-pass").andExpect(status().isUnauthorized());
        login(uniq("elsewhere-"), admin, "s3cret-pass").andExpect(status().isUnauthorized());   // another (unknown) restaurant
        login("", admin, "s3cret-pass").andExpect(status().isUnauthorized());                    // no restaurant: operator only
    }

    @Test
    void usernamesAreCaseInsensitiveAndNormalised() throws Exception {
        String name = uniq("Mixed");
        String cafe = uniq("cafe-");
        createRestaurant(cafe, "  " + name + "  ", "s3cret-pass");
        login(cafe, name.toUpperCase(), "s3cret-pass").andExpect(status().isOk());
        login(cafe, name.toLowerCase(), "s3cret-pass").andExpect(status().isOk());
        login(cafe.toUpperCase(), name, "s3cret-pass").andExpect(status().isOk());              // the identifier too
    }

    @Test
    void adminCreatesStaffWhoSignsInAndSeesOnlyOwnTenant() throws Exception {
        String admin = uniq("owner");
        String cafe = uniq("cafe-");
        String tenant = createRestaurant(cafe, admin, "s3cret-pass");
        String adminToken = tokenOf(cafe, admin, "s3cret-pass");

        String staff = uniq("waiter");
        adminCreateStaff(adminToken, staff, "STAFF", "waiter-pass1").andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(staff))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        login(cafe, staff, "waiter-pass1").andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STAFF")).andExpect(jsonPath("$.tenantId").value(tenant));
        String staffToken = tokenOf(cafe, staff, "waiter-pass1");
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
        String cafe = uniq("cafe-");
        createRestaurant(cafe, admin, "s3cret-pass");
        String adminToken = tokenOf(cafe, admin, "s3cret-pass");
        String staff = uniq("waiter");
        adminCreateStaff(adminToken, staff, "STAFF", "waiter-pass1").andExpect(status().isCreated());

        adminCreateStaff(tokenOf(cafe, staff, "waiter-pass1"), uniq("x"), "STAFF", "another-pass1")
                .andExpect(status().isForbidden());
        adminCreateStaff(adminToken, uniq("manager"), "RESTAURANT_ADMIN", "manager-pass1")
                .andExpect(status().isCreated());
    }

    @Test
    void sameUsernameInTwoRestaurantsAreTwoSeparateAccounts() throws Exception {
        String name = uniq("shared");
        String cafeA = uniq("a-");
        String cafeB = uniq("b-");
        String tenantA = createRestaurant(cafeA, name, "pass-for-a-1");
        String tenantB = createRestaurant(cafeB, name, "pass-for-b-2");        // the same username, another restaurant
        assertThat(tenantA).isNotEqualTo(tenantB);

        // each signs in to its own restaurant with its own password and lands in its own tenant
        login(cafeA, name, "pass-for-a-1").andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value(tenantA));
        login(cafeB, name, "pass-for-b-2").andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value(tenantB));
        login(cafeA, name, "pass-for-b-2").andExpect(status().isUnauthorized());
        login(cafeB, name, "pass-for-a-1").andExpect(status().isUnauthorized());

        // the staff of one restaurant can use a name another restaurant already has, but not twice in the same one
        String adminToken = tokenOf(cafeA, name, "pass-for-a-1");
        String waiter = uniq("waiter");
        adminCreateStaff(adminToken, waiter, "STAFF", "waiter-pass1").andExpect(status().isCreated());
        adminCreateStaff(adminToken, waiter.toUpperCase(), "STAFF", "waiter-pass1").andExpect(status().isConflict());
        adminCreateStaff(tokenOf(cafeB, name, "pass-for-b-2"), waiter, "STAFF", "waiter-pass2").andExpect(status().isCreated());
    }

    @Test
    void aTokenStaysWithItsOwnRestaurantWhenTheNameRepeats() throws Exception {
        String name = uniq("shared");
        String cafeA = uniq("a-");
        String cafeB = uniq("b-");
        createRestaurant(cafeA, name, "pass-for-a-1");
        String tenantB = createRestaurant(cafeB, name, "pass-for-b-2");
        String tokenB = tokenOf(cafeB, name, "pass-for-b-2");

        // refresh looks the account up by the token's restaurant, not by the bare name
        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value(tenantB));
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());
    }

    @Test
    void failedCreateWithAdminLeavesNoRestaurantBehind() throws Exception {
        String cafe = uniq("taken-");
        createRestaurant(cafe, uniq("owner"), "s3cret-pass");

        platformCreate("{\"identifier\":\"" + cafe + "\",\"name\":\"R\",\"adminUsername\":\"" + uniq("o")
                + "\",\"adminPassword\":\"s3cret-pass\"}").andExpect(status().isConflict());
        // nothing of the failed attempt was left behind: the same admin name works in a restaurant that is free
        createRestaurant(uniq("free-"), uniq("owner"), "s3cret-pass");
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        String admin = uniq("owner");
        String cafe = uniq("cafe-");
        createRestaurant(cafe, admin, "s3cret-pass");
        String token = tokenOf(cafe, admin, "s3cret-pass");

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
        String cafe = uniq("cafe-");
        createRestaurant(cafe, admin, "s3cret-pass");
        String adminToken = tokenOf(cafe, admin, "s3cret-pass");
        String staff = uniq("waiter");
        String accountId = JsonPath.read(adminCreateStaff(adminToken, staff, "STAFF", "waiter-pass1")
                .andReturn().getResponse().getContentAsString(), "$.accountId");
        String staffToken = tokenOf(cafe, staff, "waiter-pass1");

        mvc.perform(delete("/api/admin/staff/{id}", accountId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DEACTIVATED"));

        login(cafe, staff, "waiter-pass1").andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedRestaurantLocksItsUsersOut() throws Exception {
        String admin = uniq("owner");
        String cafe = uniq("cafe-");
        String tenant = createRestaurant(cafe, admin, "s3cret-pass");
        login(cafe, admin, "s3cret-pass").andExpect(status().isOk());

        mvc.perform(delete("/api/platform/restaurants/{id}", tenant).with(httpBasic("platform", "platform")))
                .andExpect(status().isOk());

        login(cafe, admin, "s3cret-pass").andExpect(status().isUnauthorized());
    }

    @Test
    void unknownUserIsRejected() throws Exception {
        login(uniq("nowhere-"), uniq("ghost"), "whatever-pass").andExpect(status().isUnauthorized());
    }
}
