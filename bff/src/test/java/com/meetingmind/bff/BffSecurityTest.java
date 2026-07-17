package com.meetingmind.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meetingmind.bff.config.BffSessionLifetimePolicy;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(BffSecurityTest.TestEndpoints.class)
class BffSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @Autowired
    private BffSessionLifetimePolicy sessionLifetimePolicy;

    @Test
    void exposesCsrfTokenWithoutCaching() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"));
    }

    @Test
    void rejectsProtectedRequestWithoutAuthenticationInsteadOfRedirecting() throws Exception {
        mockMvc.perform(get("/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    void requiresCsrfForAuthenticatedStateChangingRequest() throws Exception {
        mockMvc.perform(post("/test/state-change").with(user("user")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/test/state-change").with(user("user")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void changesSessionIdAfterAuthentication() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        String previousSessionId = session.getId();

        sessionAuthenticationStrategy.onAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("user", "n/a", java.util.List.of()),
                request,
                new MockHttpServletResponse());

        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession(false).getId()).isNotEqualTo(previousSessionId);
    }

    @Test
    void bindsApprovedSessionLifetimes() {
        assertThat(sessionLifetimePolicy.standardIdle()).isEqualTo(Duration.ofMinutes(60));
        assertThat(sessionLifetimePolicy.standardAbsolute()).isEqualTo(Duration.ofHours(12));
        assertThat(sessionLifetimePolicy.rememberIdle()).isEqualTo(Duration.ofDays(7));
        assertThat(sessionLifetimePolicy.rememberAbsolute()).isEqualTo(Duration.ofDays(14));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestEndpoints {

        @Bean
        SessionRepository<MapSession> testSessionRepository() {
            return new MapSessionRepository(new ConcurrentHashMap<>());
        }

        @RestController
        static class TestController {

            @GetMapping("/test/protected")
            ResponseEntity<Void> protectedEndpoint() {
                return ResponseEntity.noContent().build();
            }

            @PostMapping("/test/state-change")
            ResponseEntity<Void> stateChange() {
                return ResponseEntity.noContent().build();
            }
        }
    }
}
