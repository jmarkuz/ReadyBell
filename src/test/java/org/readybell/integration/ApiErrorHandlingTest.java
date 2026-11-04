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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Client mistakes must come back as 4xx problem details, never as 500. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiErrorHandlingTest {

    @Autowired private MockMvc mvc;
    @MockBean private NotificationSender notificationSender;

    @Test
    void unknownRouteIs404() throws Exception {
        mvc.perform(get("/api/does-not-exist").with(httpBasic("admin", "admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void wrongMethodIs405() throws Exception {
        mvc.perform(delete("/api/orders").with(httpBasic("admin", "admin")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void malformedPathIdIs400() throws Exception {
        mvc.perform(patch("/api/orders/not-a-uuid/status").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Parameter 'id' has an invalid value."));
    }

    @Test
    void malformedQueryNumberIs400() throws Exception {
        mvc.perform(get("/api/orders?page=abc").with(httpBasic("admin", "admin")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedMediaTypeIs415() throws Exception {
        mvc.perform(post("/api/orders").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void invalidSortIsGenericInvalidRequest() throws Exception {
        mvc.perform(get("/api/orders?sortBy=bogus").with(httpBasic("admin", "admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Request"));
    }
}
