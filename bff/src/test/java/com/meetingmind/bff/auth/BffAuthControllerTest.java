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
    private CompatibilityAuthClient compatibilityAuthClient;

    @MockitoBean
    private BffSessionManager sessionManager;

    @MockitoBean
    private BffTokenManager tokenManager;

    private LegacyAuthTokenResponse tokens;
    private BffAuthenticatedResponse browserResponse;

    @BeforeEach
    void setUp() {
        tokens = new LegacyAuthTokenResponse(
                "access-secret",
                "refresh-secret",
                "Bearer",
                3_600,
                1_209_600,
                new LegacyAuthUser("user-id", "user@example.com", "User", null, "ACTIVE"));
        browserResponse = new BffAuthenticatedResponse(
                new BffAuthUser("user-id", "user@example.com", "User", null, "ACTIVE"),
                new BffSessionView(
                        Instant.parse("2026-07-16T12:00:00Z"),
                        Instant.parse("2026-07-16T01:00:00Z"),
                        false));
    }

    @Test
    void signupRequiresCsrfAndReturnsOnlyUserAndSession() throws Exception {
        when(compatibilityAuthClient.signup(any(), any())).thenReturn(tokens);
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
                .andExpect(jsonPath("$.user.id").value("user-id"))
                .andExpect(jsonPath("$.session.rememberMe").value(false))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void loginAndGoogleUseTheSameTokenlessBrowserResponse() throws Exception {
        when(compatibilityAuthClient.login(any(), any())).thenReturn(tokens);
        when(compatibilityAuthClient.google(any(), any())).thenReturn(tokens);
        when(sessionManager.establish(eq(tokens), eq(false), any(), any()))
                .thenReturn(browserResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password-123!","rememberMe":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("user-id"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        mockMvc.perform(post("/api/v1/auth/google")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"credential":"google-credential","rememberMe":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("user-id"))
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
    void logoutAllRequiresCsrfAndKeepsCredentialsOutOfTheBrowserSessionContract() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .with(csrf())
                        .with(user("user-id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Password-123!\"}"))
                .andExpect(status().isNoContent());

        verify(tokenManager).logoutAll(any(), any());
    }

    @Test
    void passwordResetEndpointsArePublicButCsrfProtected() throws Exception {
        when(tokenManager.requestPasswordReset(any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/password-reset-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/password-reset-requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true));

        mockMvc.perform(post("/api/v1/auth/password-resets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"mmpr_example\",\"newPassword\":\"Password-456!\"}"))
                .andExpect(status().isNoContent());

        verify(tokenManager).requestPasswordReset(any(), any());
        verify(tokenManager).resetPassword(any());
    }

    @Test
    void authenticatedAccountChangesDoNotTakeUserOrSessionIdsFromTheBrowser() throws Exception {
        BffAuthUser updated = new BffAuthUser("user-id", "user@example.com", "Updated", null, "ACTIVE");
        when(tokenManager.updateProfile(any(), any())).thenReturn(updated);

        mockMvc.perform(post("/api/v1/auth/password")
                        .with(csrf())
                        .with(user("user-id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password-123!\",\"newPassword\":\"Password-456!\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/auth/profile")
                        .with(csrf())
                        .with(user("user-id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated"));

        verify(tokenManager).changePassword(any(), any());
        verify(tokenManager).updateProfile(any(), any());
        verify(sessionManager).updateCurrentUser(eq(updated), any(), any());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSessionConfiguration {

        @Bean
        SessionRepository<MapSession> testSessionRepository() {
            return new MapSessionRepository(new ConcurrentHashMap<>());
        }
    }
}
