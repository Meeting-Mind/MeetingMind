package com.meetingmind.bff.auth;

import com.meetingmind.bff.tokenvault.TokenVault;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;

@Service
public class BffAllDeviceLogoutService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(BffAllDeviceLogoutService.class);

    private final AuthClient authClient;
    private final TokenVault tokenVault;
    private final ObjectProvider<SessionRepository<?>> sessionRepositoryProvider;

    public BffAllDeviceLogoutService(
            AuthClient authClient,
            TokenVault tokenVault,
            ObjectProvider<SessionRepository<?>> sessionRepositoryProvider) {
        this.authClient = authClient;
        this.tokenVault = tokenVault;
        this.sessionRepositoryProvider = sessionRepositoryProvider;
    }

    public void reauthenticate(
            BrowserAuthRequests.Reauthenticate request,
            HttpServletRequest servletRequest) {
        validateRequest(request);
        CurrentSession current = requireCurrentSession(servletRequest);
        Instant authenticatedAt = authClient.reauthenticate(
                current.authSessionId(),
                current.authUserId(),
                request);
        if (authenticatedAt == null) {
            throw authenticationServiceError();
        }
        try {
            current.session().setAttribute(
                    BffSessionAttributes.AUTHENTICATED_AT,
                    authenticatedAt);
        } catch (RuntimeException exception) {
            throw sessionCleanupUnavailable();
        }
    }

    public void logoutAll(HttpServletRequest servletRequest) {
        CurrentSession current = requireCurrentSession(servletRequest);
        authClient.revokeAll(
                current.authSessionId(),
                current.authUserId(),
                current.authenticatedAt());

        FindByIndexNameSessionRepository<Session> repository = indexedRepository();
        Map<String, Session> indexedSessions;
        try {
            indexedSessions = repository.findByPrincipalName(
                    current.authUserId().toString());
        } catch (RuntimeException exception) {
            throw sessionCleanupUnavailable();
        }

        indexedSessions.values().stream()
                .filter(session -> !session.getId().equals(current.session().getId()))
                .sorted(Comparator.comparing(Session::getId))
                .forEach(session -> deleteIndexedSession(repository, session));

        deleteTokenBundle(current.session().getAttribute(
                BffSessionAttributes.TOKEN_BUNDLE_ID));
        try {
            current.session().invalidate();
        } catch (RuntimeException exception) {
            throw sessionCleanupUnavailable();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private CurrentSession requireCurrentSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw invalidSession();
        }
        Object authUserId = session.getAttribute(BffSessionAttributes.AUTH_USER_ID);
        Object authSessionId = session.getAttribute(BffSessionAttributes.AUTH_SESSION_ID);
        Object authenticatedAt = session.getAttribute(BffSessionAttributes.AUTHENTICATED_AT);
        if (!(authUserId instanceof UUID userId)
                || !(authSessionId instanceof UUID sessionId)
                || !(authenticatedAt instanceof Instant authenticationTime)) {
            throw invalidSession();
        }
        return new CurrentSession(
                session,
                userId,
                sessionId,
                authenticationTime);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private FindByIndexNameSessionRepository<Session> indexedRepository() {
        SessionRepository<?> repository = sessionRepositoryProvider.getIfAvailable();
        if (!(repository instanceof FindByIndexNameSessionRepository indexed)) {
            throw BffAuthException.of(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SESSION_INDEX_UNAVAILABLE",
                    "전체 로그아웃을 위한 세션 저장소를 사용할 수 없습니다.");
        }
        return (FindByIndexNameSessionRepository<Session>) indexed;
    }

    private void deleteIndexedSession(
            FindByIndexNameSessionRepository<Session> repository,
            Session session) {
        deleteTokenBundle(session.getAttribute(
                BffSessionAttributes.TOKEN_BUNDLE_ID));
        try {
            repository.deleteById(session.getId());
        } catch (RuntimeException exception) {
            throw sessionCleanupUnavailable();
        }
    }

    private void deleteTokenBundle(Object tokenBundleId) {
        if (!(tokenBundleId instanceof UUID bundleId)) {
            return;
        }
        try {
            tokenVault.delete(bundleId);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "event=logout_all_token_bundle_cleanup_deferred reason=vault_delete_failed");
        }
    }

    private void validateRequest(BrowserAuthRequests.Reauthenticate request) {
        boolean passwordPresent =
                request.password() != null && !request.password().isBlank();
        boolean credentialPresent =
                request.credential() != null && !request.credential().isBlank();
        boolean valid = switch (request.method()) {
            case "PASSWORD" -> passwordPresent && !credentialPresent;
            case "GOOGLE" -> credentialPresent && !passwordPresent;
            default -> false;
        };
        if (!valid) {
            throw BffAuthException.of(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    "재인증 요청값이 올바르지 않습니다.");
        }
    }

    private BffAuthException invalidSession() {
        return BffAuthException.of(
                HttpStatus.UNAUTHORIZED,
                "SESSION_INVALID",
                "로그인이 만료되었습니다. 다시 로그인해 주세요.");
    }

    private BffAuthException authenticationServiceError() {
        return BffAuthException.of(
                HttpStatus.BAD_GATEWAY,
                "AUTH_SERVICE_INVALID_RESPONSE",
                "인증 요청을 처리하지 못했습니다.");
    }

    private BffAuthException sessionCleanupUnavailable() {
        return BffAuthException.of(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SESSION_CLEANUP_UNAVAILABLE",
                "모든 기기 로그아웃을 완료하지 못했습니다. 다시 시도해 주세요.");
    }

    private record CurrentSession(
            HttpSession session,
            UUID authUserId,
            UUID authSessionId,
            Instant authenticatedAt) {
    }
}
