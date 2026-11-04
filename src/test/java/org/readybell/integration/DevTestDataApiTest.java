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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The dev-only test-data endpoints: generate, import from a file, delete. */
@SpringBootTest
@AutoConfigureMockMvc
// "test" last so its H2 datasource and users override the dev profile's local PostgreSQL settings
@ActiveProfiles({"dev", "test"})
class DevTestDataApiTest {

    @Autowired private MockMvc mvc;
    @MockBean private NotificationSender notificationSender;

    private int totalTestOrders() throws Exception {
        String body = mvc.perform(get("/api/orders").param("search", "(test)").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.totalCount");
    }

    @Test
    void generateImportAndDelete() throws Exception {
        mvc.perform(delete("/api/dev/test-data").with(httpBasic("admin", "admin"))).andExpect(status().isOk());
        assertEquals(0, totalTestOrders());

        mvc.perform(post("/api/dev/test-data").param("count", "5").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(5));
        assertEquals(5, totalTestOrders());

        String file = """
                {"orders":[
                  {"customerName":"Imported Ann","estimatedMinutes":10,"status":"READY","items":[{"name":"Latte","quantity":2}]},
                  {"status":"CANCELLED"},
                  {"customerName":"Imported Bob"}
                ]}""";
        mvc.perform(post("/api/dev/test-data/import").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(file))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(3));
        assertEquals(8, totalTestOrders());

        mvc.perform(delete("/api/dev/test-data").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(8));
        assertEquals(0, totalTestOrders());
    }

    @Test
    void importRejectsBadRecords() throws Exception {
        mvc.perform(post("/api/dev/test-data/import").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orders\":[{\"status\":\"BOGUS\"}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/dev/test-data/import").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orders\":[{\"items\":[{\"name\":\"\",\"quantity\":0}]}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/dev/test-data/import").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orders\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
