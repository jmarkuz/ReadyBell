package org.readybell.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.ordering.application.CreateOrderCommand;
import org.readybell.ordering.application.ManageOrderUseCase;
import org.readybell.ordering.domain.OrderRepository;
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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Per-restaurant sequential order numbers: allocation under concurrency, the API, sorting and search. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderNumberApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ManageOrderUseCase manageOrders;
    @Autowired private CustomerSessionRepository sessions;
    @Autowired private OrderRepository orders;
    @MockBean private NotificationSender notificationSender;

    @Test
    void fiftyParallelCreatesGetUniqueGaplessNumbers() throws Exception {
        var tenant = new TenantId(UUID.randomUUID());
        var session = CustomerSession.start(tenant, Clock.systemUTC());
        sessions.save(session);

        int n = 50;
        var pool = Executors.newFixedThreadPool(n);
        var start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return manageOrders.createOrder(new CreateOrderCommand(tenant, session.getId())).getOrderNumber();
            }));
        }
        start.countDown();
        List<Integer> numbers = new ArrayList<>();
        for (Future<Integer> f : futures) {
            numbers.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(numbers).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, n).boxed().toList());
        // and what was persisted agrees
        assertThat(orders.findAllByTenant(tenant)).extracting(o -> o.getOrderNumber())
                .containsExactlyInAnyOrderElementsOf(numbers);
    }

    @Test
    void apiReturnsConsecutiveNumbersAndSortsAndSearchesByThem() throws Exception {
        var tenant = new TenantId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));  // the "admin" restaurant
        var session = CustomerSession.start(tenant, Clock.systemUTC());
        sessions.save(session);

        int first = 0;
        int last = 0;
        for (int i = 0; i < 12; i++) {
            String body = mvc.perform(post("/api/orders").with(httpBasic("admin", "admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sessionId\":\"" + session.getId().value() + "\"}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            String display = JsonPath.read(body, "$.orderId");
            assertThat(display).matches("#[1-9][0-9]*");
            int number = Integer.parseInt(display.substring(1));
            if (i == 0) {
                first = number;
            } else {
                assertThat(number).isEqualTo(last + 1);
            }
            last = number;
        }
        assertThat(last).isEqualTo(first + 11);

        String asc = mvc.perform(get("/api/orders?sortBy=orderId&sortOrder=asc&pageSize=100").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(asc, "$.items[*].orderId");
        List<Integer> nums = ids.stream().map(s -> Integer.parseInt(s.substring(1))).toList();
        assertThat(nums).isSorted().contains(first, last);

        String desc = mvc.perform(get("/api/orders?sortBy=orderId&sortOrder=desc&pageSize=100").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> idsDesc = JsonPath.read(desc, "$.items[*].orderId");
        assertThat(idsDesc.get(0)).isEqualTo("#" + nums.get(nums.size() - 1));

        for (String term : new String[]{"#" + last, String.valueOf(last)}) {
            String found = mvc.perform(get("/api/orders").param("search", term).with(httpBasic("admin", "admin")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<String> hit = JsonPath.read(found, "$.items[*].orderId");
            assertThat(hit).contains("#" + last);
        }
    }
}
