package org.readybell.integration;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Guards the cost of the order hot paths in SQL statements (measured with Hibernate statistics on PostgreSQL). The
 * limits are the current numbers: a change that adds a query to creating an order or changing its status has to
 * raise them on purpose.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderStatementCountTest {

    private static final String ITEMS =
            "[{\"name\":\"Burger\",\"quantity\":1},{\"name\":\"Fries\",\"quantity\":2},{\"name\":\"Cola\",\"quantity\":1}]";

    @Autowired private MockMvc mvc;
    @Autowired private EntityManagerFactory emf;
    @MockBean private NotificationSender notificationSender;

    private Statistics stats() {
        return emf.unwrap(SessionFactory.class).getStatistics();
    }

    private String createOrder(String customer) throws Exception {
        String body = mvc.perform(post("/api/orders").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"" + customer + "\",\"items\":" + ITEMS + "}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void creatingAnOrderInsertsWithoutALookupOrAnExtraUpdate() throws Exception {
        createOrder("warm-up");
        stats().clear();

        createOrder("measured");

        // session insert + its tenant check, order number, order insert, items; no merge SELECT, no UPDATE of the version
        assertThat(stats().getEntityUpdateCount()).as("entity updates while creating").isZero();
        assertThat(stats().getPrepareStatementCount()).as("statements to create an order").isLessThanOrEqualTo(8);
    }

    @Test
    void changingTheStatusTakesFourStatements() throws Exception {
        String id = createOrder("status");
        stats().clear();

        mvc.perform(patch("/api/orders/{id}/status", id).with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"));

        // load order, load its items, insert the outbox event, update the order
        assertThat(stats().getPrepareStatementCount()).as("statements for a status change").isLessThanOrEqualTo(4);
    }
}
