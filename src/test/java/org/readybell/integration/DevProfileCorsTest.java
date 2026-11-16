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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The dev profile lets the Vite dev server (another origin) call the API. */
@SpringBootTest
@AutoConfigureMockMvc
// "test" last so its Testcontainers datasource, Flyway settings and users override the dev profile's local PostgreSQL settings
@ActiveProfiles({"dev", "test"})
class DevProfileCorsTest {

    @Autowired private MockMvc mvc;
    @MockBean private NotificationSender notificationSender;

    @Test
    void devProfileAcceptsTheViteDevServerOrigin() throws Exception {
        mvc.perform(post("/api/login").header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void theRemovedTestDataEndpointsAreGone() throws Exception {
        mvc.perform(post("/api/dev/test-data").with(httpBasic("admin", "admin")))
                .andExpect(status().isNotFound());
    }
}
