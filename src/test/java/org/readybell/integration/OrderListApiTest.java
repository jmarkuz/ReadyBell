package org.readybell.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Staff order list: details on create, pagination, filters, sorting, search and polling. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderListApiTest {

    private static final TenantId TENANT_A = new TenantId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));

    @Autowired private MockMvc mvc;
    @Autowired private CustomerSessionRepository sessions;
    @MockBean private NotificationSender notificationSender;

    private String tag;          // unique per test so tests do not see each other's orders
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        tag = "t" + UUID.randomUUID().toString().substring(0, 8);
        var session = CustomerSession.start(TENANT_A, Clock.systemUTC());
        sessions.save(session);
        sessionId = session.getId().value();
    }

    private MvcResult create(String body) throws Exception {
        return mvc.perform(post("/api/orders").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
    }

    private String createOrder(String customer, String itemsJson, Integer minutes) throws Exception {
        String body = "{\"customerSessionId\":\"" + sessionId + "\",\"customerName\":\"" + customer + "\""
                + (itemsJson == null ? "" : ",\"items\":" + itemsJson)
                + (minutes == null ? "" : ",\"estimatedMinutes\":" + minutes) + "}";
        return JsonPath.read(create(body).getResponse().getContentAsString(), "$.id");
    }

    private MvcResult list(String query) throws Exception {
        return mvc.perform(get("/api/orders?" + query).with(httpBasic("admin", "admin")))
                .andExpect(status().isOk()).andReturn();
    }

    private int totalFor(String search) throws Exception {
        String body = mvc.perform(get("/api/orders").param("search", search).with(httpBasic("admin", "admin")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.totalCount");
    }

    private String json(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString();
    }

    @Test
    void createStoresDetailsAndReturnsDashboardShape() throws Exception {
        String res = json(create("{\"sessionId\":\"" + sessionId + "\",\"customerName\":\"" + tag
                + "\",\"estimatedMinutes\":15,\"items\":[{\"name\":\"Burger\",\"quantity\":2},{\"menuItemId\":\"Fries\",\"quantity\":1}]}"));
        assertThat((String) JsonPath.read(res, "$.orderId")).matches("#[0-9A-F]{8}");
        assertThat((String) JsonPath.read(res, "$.status")).isEqualTo("CREATED");
        assertThat((String) JsonPath.read(res, "$.id")).hasSize(36);

        String row = json(list("search=" + tag));
        assertThat((Integer) JsonPath.read(row, "$.totalCount")).isEqualTo(1);
        assertThat((String) JsonPath.read(row, "$.items[0].itemsOrdered")).isEqualTo("2x Burger, Fries");
        assertThat((Integer) JsonPath.read(row, "$.items[0].itemCount")).isEqualTo(3);
        assertThat((String) JsonPath.read(row, "$.items[0].customerName")).isEqualTo(tag);
        int wait = JsonPath.read(row, "$.items[0].estimatedWaitTime");
        assertThat(wait).isBetween(13, 15);
        assertThat((Object) JsonPath.read(row, "$.items[0].deliveryETA")).isNotNull();
    }

    @Test
    void orderWithoutDetailsStillWorks() throws Exception {
        create("{\"sessionId\":\"" + sessionId + "\"}");
        mvc.perform(get("/api/orders").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(1));
    }

    @Test
    void invalidItemsAreRejected() throws Exception {
        for (String items : new String[]{"[{\"name\":\"\",\"quantity\":1}]", "[{\"name\":\"X\",\"quantity\":0}]",
                                         "[{\"name\":\"X\",\"quantity\":100}]"}) {
            mvc.perform(post("/api/orders").with(httpBasic("admin", "admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sessionId\":\"" + sessionId + "\",\"items\":" + items + "}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void paginationReportsTotalsAndPages() throws Exception {
        for (int i = 0; i < 5; i++) createOrder(tag + "-" + i, null, null);
        String p1 = json(list("search=" + tag + "&pageSize=2&page=1"));
        String p3 = json(list("search=" + tag + "&pageSize=2&page=3"));
        assertThat((Integer) JsonPath.read(p1, "$.totalCount")).isEqualTo(5);
        assertThat((Integer) JsonPath.read(p1, "$.pageCount")).isEqualTo(3);
        assertThat((Integer) JsonPath.read(p1, "$.items.length()")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(p3, "$.items.length()")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(p3, "$.currentPage")).isEqualTo(3);
    }

    @Test
    void sortsByCustomerNameBothDirections() throws Exception {
        createOrder(tag + "-b", null, null);
        createOrder(tag + "-a", null, null);
        createOrder(tag + "-c", null, null);
        String asc = json(list("search=" + tag + "&sortBy=customerName&sortOrder=asc"));
        String desc = json(list("search=" + tag + "&sortBy=customerName&sortOrder=desc"));
        assertThat((String) JsonPath.read(asc, "$.items[0].customerName")).isEqualTo(tag + "-a");
        assertThat((String) JsonPath.read(desc, "$.items[0].customerName")).isEqualTo(tag + "-c");
    }

    @Test
    void filtersByStatusRepeatedAndCommaSeparated() throws Exception {
        String a = createOrder(tag + "-a", null, null);
        createOrder(tag + "-b", null, null);
        mvc.perform(patch("/api/orders/{id}/status", a).with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isOk());

        assertThat((Integer) JsonPath.read(json(list("search=" + tag + "&status=IN_PROGRESS")), "$.totalCount")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(json(list("search=" + tag + "&status=CREATED,IN_PROGRESS")), "$.totalCount")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(json(list("search=" + tag + "&status=CREATED&status=IN_PROGRESS")), "$.totalCount")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(json(list("search=" + tag + "&status=READY")), "$.totalCount")).isZero();
    }

    @Test
    void searchMatchesItemNameAndOrderNumberAndTreatsWildcardsLiterally() throws Exception {
        String id = createOrder(tag, "[{\"name\":\"Zucchini-" + tag + "\",\"quantity\":1}]", null);
        assertThat(totalFor("zucchini-" + tag.toUpperCase())).isEqualTo(1);
        assertThat(totalFor("#" + id.substring(0, 8).toUpperCase())).isEqualTo(1);
        assertThat(totalFor("%")).isZero();            // would match everything if treated as a wildcard
        assertThat(totalFor("_" + tag.substring(1))).isZero();
    }

    @Test
    void badQueryParametersAreRejected() throws Exception {
        for (String q : new String[]{"sortBy=nope", "sortOrder=sideways", "pageSize=101", "pageSize=0", "page=0", "status=BOGUS"}) {
            mvc.perform(get("/api/orders?" + q).with(httpBasic("admin", "admin")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void otherTenantSeesNothing() throws Exception {
        createOrder(tag, null, null);
        mvc.perform(get("/api/orders?search=" + tag).with(httpBasic("other", "other")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void pollReturnsChangesAnd304WhenNothingChanged() throws Exception {
        String id = createOrder(tag, null, null);
        MvcResult first = mvc.perform(get("/api/orders/poll").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk()).andReturn();
        String etag = JsonPath.read(json(first), "$.eTag");
        assertThat(first.getResponse().getHeader("ETag")).isEqualTo(etag);
        assertThat((Integer) JsonPath.read(json(first), "$.orders.length()")).isGreaterThanOrEqualTo(1);

        mvc.perform(get("/api/orders/poll").param("eTag", etag).with(httpBasic("admin", "admin")))
                .andExpect(status().isNotModified());

        mvc.perform(patch("/api/orders/{id}/status", id).with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/orders/poll").param("eTag", etag).with(httpBasic("admin", "admin")))
                .andExpect(status().isOk());
    }

    @Test
    void pollRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/orders/poll")).andExpect(status().isUnauthorized());
    }
}
