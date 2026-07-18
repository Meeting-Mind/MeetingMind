package com.meetingmind.bff.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meetingmind.bff.tokenvault.TokenVault;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

class BffAllDeviceLogoutServiceTest {

    private static final UUID AUTH_USER_ID =
            UUID.fromString("0a5b7c1e-5d75-4dc0-a10e-a330d0583930");
    private static final UUID AUTH_SESSION_ID =
            UUID.fromString("e655a7be-39b1-44eb-9559-419ea96e5c62");
    private static final UUID CURRENT_BUNDLE_ID =
            UUID.fromString("56a47014-c824-46a4-a832-81f753b7903b");
    private static final UUID OTHER_BUNDLE_ID =
            UUID.fromString("149bf588-e80d-4db6-8c75-1a3d701e9356");
    private static final Instant AUTHENTICATED_AT =
            Instant.parse("2026-07-18T04:10:00Z");

    private AuthClient authClient;
    private TokenVault tokenVault;
    private FindByIndexNameSessionRepository<Session> repository;
    private BffAllDeviceLogoutService service;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        authClient = mock(AuthClient.class);
        tokenVault = mock(TokenVault.class);
        repository = mock(FindByIndexNameSessionRepository.class);
        ObjectProvider<SessionRepository<?>> provider = mock(ObjectProvider.class);
        doReturn(repository).when(provider).getIfAvailable();
        service = new BffAllDeviceLogoutService(authClient, tokenVault, provider);
    }

    @Test
    void storesOnlyTheServerReauthenticationTimeInTheCurrentSession() {
        MockHttpServletRequest request = authenticatedRequest();
        Instant serverTime = Instant.parse("2026-07-18T04:11:00Z");
        BrowserAuthRequests.Reauthenticate proof =
                new BrowserAuthRequests.Reauthenticate(
                        "PASSWORD",
                        "password-123!",
                        null);
        when(authClient.reauthenticate(AUTH_SESSION_ID, AUTH_USER_ID, proof))
                .thenReturn(serverTime);

        service.reauthenticate(proof, request);

        assertThat(request.getSession(false).getAttribute(
                BffSessionAttributes.AUTHENTICATED_AT))
                .isEqualTo(serverTime);
    }

    @Test
    void revokesAuthThenDeletesOtherIndexedSessionsBeforeTheCurrentSession() {
        MockHttpServletRequest request = authenticatedRequest();
        HttpSession currentHttpSession = request.getSession(false);
        MapSession current = new MapSession(currentHttpSession.getId());
        current.setAttribute(
                BffSessionAttributes.TOKEN_BUNDLE_ID,
                CURRENT_BUNDLE_ID);
        MapSession other = new MapSession();
        other.setAttribute(
                BffSessionAttributes.TOKEN_BUNDLE_ID,
                OTHER_BUNDLE_ID);
        when(repository.findByPrincipalName(AUTH_USER_ID.toString()))
                .thenReturn(Map.of(
                        current.getId(), current,
                        other.getId(), other));

        service.logoutAll(request);

        verify(authClient).revokeAll(
                AUTH_SESSION_ID,
                AUTH_USER_ID,
                AUTHENTICATED_AT);
        verify(tokenVault).delete(OTHER_BUNDLE_ID);
        verify(tokenVault).delete(CURRENT_BUNDLE_ID);
        verify(repository).deleteById(other.getId());
        verify(repository, never()).deleteById(current.getId());
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void keepsTheCurrentSessionForRetryWhenAnotherIndexedSessionCannotBeDeleted() {
        MockHttpServletRequest request = authenticatedRequest();
        MapSession other = new MapSession();
        other.setAttribute(
                BffSessionAttributes.TOKEN_BUNDLE_ID,
                OTHER_BUNDLE_ID);
        when(repository.findByPrincipalName(AUTH_USER_ID.toString()))
                .thenReturn(Map.of(other.getId(), other));
        doThrow(new IllegalStateException("redis unavailable"))
                .when(repository)
                .deleteById(other.getId());

        assertThatThrownBy(() -> service.logoutAll(request))
                .isInstanceOfSatisfying(BffAuthException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo("SESSION_CLEANUP_UNAVAILABLE"));

        assertThat(request.getSession(false)).isNotNull();
        verify(tokenVault, never()).delete(CURRENT_BUNDLE_ID);
    }

    @Test
    void rejectsAmbiguousReauthenticationProofBeforeCallingAuth() {
        MockHttpServletRequest request = authenticatedRequest();
        BrowserAuthRequests.Reauthenticate ambiguous =
                new BrowserAuthRequests.Reauthenticate(
                        "PASSWORD",
                        "password-123!",
                        "google-credential");

        assertThatThrownBy(() -> service.reauthenticate(ambiguous, request))
                .isInstanceOfSatisfying(BffAuthException.class, exception ->
                        assertThat(exception.code()).isEqualTo("INVALID_REQUEST"));

        verify(authClient, never()).reauthenticate(any(), any(), any());
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BffSessionAttributes.AUTH_USER_ID, AUTH_USER_ID);
        session.setAttribute(BffSessionAttributes.AUTH_SESSION_ID, AUTH_SESSION_ID);
        session.setAttribute(
                BffSessionAttributes.TOKEN_BUNDLE_ID,
                CURRENT_BUNDLE_ID);
        session.setAttribute(
                BffSessionAttributes.AUTHENTICATED_AT,
                AUTHENTICATED_AT);
        request.setSession(session);
        return request;
    }
}
