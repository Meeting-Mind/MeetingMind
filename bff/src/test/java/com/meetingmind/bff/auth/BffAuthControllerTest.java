package com.meetingmind.bff.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(BffAuthControllerTest.TestSessionConfiguration.class)
class BffAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthClient authClient;

    @MockitoBean
    private BffSessionManager sessionManager;

    @MockitoBean
    private BffTokenManager tokenManager;

    @MockitoBean
    private BffAllDeviceLogoutService allDeviceLogoutService;

    private AuthTokenResponse tokens;
    private BffAuthenticatedResponse browserResponse;

    @BeforeEach
    void setUp() {
        UUID authUserId = UUID.fromString("0a5b7c1e-5d75-4dc0-a10e-a330d0583930");
        String resourceUserId = "user-" + authUserId;
        tokens = new AuthTokenResponse(
                AuthTokenResponse.LEGACY_SCHEMA_VERSION,
                UUID.fromString("e655a7be-39b1-44eb-9559-419ea96e5c62"),
                Map.of(
                        AuthTokenResponse.LEGACY_AUDIENCE,
                        new AuthTokenResponse.AccessToken("access-secret", 3_600)),
                "refresh-secret",
                "Bearer",
                1_209_600,
                new AuthTokenResponse.User(
                        authUserId,
                        resourceUserId,
                        "user@example.com",
                        "User",
                        null,
                        "ACTIVE"));
        browserResponse = new BffAuthenticatedResponse(
                new BffAuthUser(resourceUserId, "user@example.com", "User", null, "ACTIVE"),
                new BffSessionView(
                        Instant.parse("2026-07-16T12:00:00Z"),
                        Instant.parse("2026-07-16T01:00:00Z"),
                        false));
    }

    @Test
    void signupRequiresCsrfAndReturnsOnlyUserAndSession() throws Exception {
        when(authClient.signup(any(), any())).thenReturn(tokens);
        when(sessionManager.establish(eq(tokens), eq(false), any(), any()))
                .thenReturn(browserResponse);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"user@example.com",
                                  "password":"password-123!",
                                  "displayName":"User",
                                  "rememberMe":false
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"user@example.com",
                                  "password":"password-123!",
                                  "displayName":"User",
                                  "rememberMe":false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.id").value("user-0a5b7c1e-5d75-4dc0-a10e-a330d0583930"))
                .andExpect(jsonPath("$.session.rememberMe").value(false))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void loginAndGoogleUseTheSameTokenlessBrowserResponse() throws Exception {
        when(authClient.login(any(), any())).thenReturn(tokens);
        when(authClient.google(any(), any())).thenReturn(tokens);
        when(sessionManager.establish(eq(tokens), eq(false), any(), any()))
                .thenReturn(browserResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password-123!","rememberMe":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("user-0a5b7c1e-5d75-4dc0-a10e-a330d0583930"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        mockMvc.perform(post("/api/v1/auth/google")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"credential":"google-credential","rememberMe":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("user-0a5b7c1e-5d75-4dc0-a10e-a330d0583930"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void sessionBootstrapIsAlwaysNoStoreAndValidationUsesTheCommonErrorShape() throws Exception {
        when(sessionManager.currentSession(nullable(Authentication.class), any()))
                .thenReturn(BffSessionBootstrapResponse.unauthenticated());

        mockMvc.perform(get("/api/v1/auth/session"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andExpect(jsonPath("$.authenticated").value(false));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"","rememberMe":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void logoutRequiresCsrfAndReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()))
                .andExpect(status().isNoContent());

        verify(tokenManager).logout(any());
    }

    @Test
    void reauthenticationRequiresTheCurrentSessionAndCsrf() throws Exception {
        String body = """
                {
                  "method":"PASSWORD",
                  "password":"password-123!"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/reauthenticate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/reauthenticate")
                        .with(user("user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/reauthenticate")
                        .with(user("user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        verify(allDeviceLogoutService).reauthenticate(any(), any());
    }

    @Test
    void logoutAllRequiresTheCurrentSessionAndCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout-all").with(csrf()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout-all").with(user("user")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .with(user("user"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(allDeviceLogoutService).logoutAll(any());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSessionConfiguration {

        @Bean
        SessionRepository<MapSession> testSessionRepository() {
            return new MapSessionRepository(new ConcurrentHashMap<>());
        }
    }
}
