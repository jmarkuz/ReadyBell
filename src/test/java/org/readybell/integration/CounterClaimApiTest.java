package org.readybell.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.ordering.application.CreateOrderCommand;
import org.readybell.ordering.application.ManageOrderUseCase;
import org.readybell.ordering.domain.Order;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.restaurant.domain.RestaurantRepository;
import org.readybell.session.domain.CustomerSession;
import org.readybell.session.domain.CustomerSessionRepository;
import org.readybell.shared.domain.TenantId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Counter QR flow over HTTP: public, anonymous, indistinguishable failures and one winner per order. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CounterClaimApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private RestaurantRepository restaurants;
    @Autowired private CustomerSessionRepository sessions;
    @Autowired private ManageOrderUseCase manageOrders;
    @MockBean private NotificationSender notificationSender;

    private record Fixture(String identifier, TenantId tenant) {}

    private Fixture restaurant() {
        var tenant = new TenantId(UUID.randomUUID());
        String identifier = "counter-" + UUID.randomUUID().toString().substring(0, 8);
        restaurants.save(Restaurant.create(tenant, identifier, RestaurantConfig.of("Café Aurora")));
        return new Fixture(identifier, tenant);
    }

    private Order order(Fixture f) {
        var session = CustomerSession.start(f.tenant(), Clock.systemUTC());
        sessions.save(session);
        return manageOrders.createOrder(new CreateOrderCommand(f.tenant(), session.getId()));
    }

    private org.springframework.test.web.servlet.ResultActions claim(String identifier, String body) throws Exception {
        return mvc.perform(post("/api/restaurants/" + identifier + "/claim")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void anonymousCustomerClaimsTheOrderAndThenTracksIt() throws Exception {
        var f = restaurant();
        var order = order(f);

        String body = claim(f.identifier(), "{\"orderNumber\":" + order.getOrderNumber() + "}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String sessionId = JsonPath.read(body, "$.sessionId");

        assertThat(sessionId).isEqualTo(order.getSessionId().value().toString());
        mvc.perform(get("/api/sessions/" + sessionId + "/track"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value("Café Aurora"))
                .andExpect(jsonPath("$.orders[0].status").value("CREATED"));
    }

    @Test
    void restaurantNameIsPublicAndUnknownRestaurantsAre404() throws Exception {
        var f = restaurant();

        mvc.perform(get("/api/restaurants/" + f.identifier() + "/public"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Café Aurora"));
        mvc.perform(get("/api/restaurants/does-not-exist/public")).andExpect(status().isNotFound());
    }

    @Test
    void unknownRestaurantUnknownNumberAndAlreadyClaimedLookIdentical() throws Exception {
        var f = restaurant();
        var order = order(f);
        String claimBody = "{\"orderNumber\":" + order.getOrderNumber() + "}";
        claim(f.identifier(), claimBody).andExpect(status().isOk());

        String alreadyClaimed = claim(f.identifier(), claimBody).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String unknownNumber = claim(f.identifier(), "{\"orderNumber\":999999}").andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String unknownRestaurant = claim("does-not-exist", claimBody).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownNumber).isEqualTo(alreadyClaimed);
        assertThat(unknownRestaurant).isEqualTo(alreadyClaimed);
    }

    @Test
    void aNumberOfAnotherRestaurantCannotBeClaimedHere() throws Exception {
        var mine = restaurant();
        var theirs = restaurant();
        var theirOrder = order(theirs);

        claim(mine.identifier(), "{\"orderNumber\":" + theirOrder.getOrderNumber() + "}").andExpect(status().isNotFound());
    }

    @Test
    void invalidBodiesAreRejected() throws Exception {
        var f = restaurant();

        claim(f.identifier(), "{\"orderNumber\":0}").andExpect(status().isBadRequest());
        claim(f.identifier(), "{\"orderNumber\":\"abc\"}").andExpect(status().isBadRequest());
        claim(f.identifier(), "{}").andExpect(status().isBadRequest());
    }

    @Test
    void ofManyParallelClaimsExactlyOneWins() throws Exception {
        var f = restaurant();
        var order = order(f);
        String body = "{\"orderNumber\":" + order.getOrderNumber() + "}";

        int n = 8;
        var pool = Executors.newFixedThreadPool(n);
        var start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return claim(f.identifier(), body).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> r : results) {
            statuses.add(r.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s != 200).allMatch(s -> s == 404);
    }
}
