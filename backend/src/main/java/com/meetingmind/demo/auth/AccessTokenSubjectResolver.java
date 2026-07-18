package com.meetingmind.demo.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.auth.target.AccessTokenValidationException;
import com.meetingmind.demo.auth.target.TargetAccessTokenValidator;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class AccessTokenSubjectResolver {

    public enum Mode {
        LEGACY_ONLY,
        DUAL,
        TARGET_ONLY
    }

    private final Mode mode;
    private final AuthTokenService legacyValidator;
    private final TargetAccessTokenValidator targetValidator;
    private final ObjectMapper objectMapper;

    public AccessTokenSubjectResolver(
            Mode mode,
            AuthTokenService legacyValidator,
            TargetAccessTokenValidator targetValidator,
            ObjectMapper objectMapper) {
        this.mode = java.util.Objects.requireNonNull(mode);
        this.legacyValidator = java.util.Objects.requireNonNull(legacyValidator);
        this.targetValidator = java.util.Objects.requireNonNull(targetValidator);
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper);
    }

    public Subject resolve(String authorizationHeader) {
        String token = bearerToken(authorizationHeader);
        Profile profile = classify(token);
        if ((mode == Mode.LEGACY_ONLY && profile != Profile.LEGACY)
                || (mode == Mode.TARGET_ONLY && profile != Profile.TARGET)) {
            throw unauthorized();
        }
        if (profile == Profile.LEGACY) {
            return Subject.legacy(legacyValidator.resolveSubject(authorizationHeader));
        }
        try {
            return Subject.target(targetValidator.validate(token).userId());
        } catch (AccessTokenValidationException exception) {
            throw unauthorized();
        }
    }

    private Profile classify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                throw unauthorized();
            }
            JsonNode header = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            if (!header.isObject()) {
                throw unauthorized();
            }
            String alg = text(header, "alg");
            String typ = text(header, "typ");
            JsonNode kid = header.get("kid");
            boolean hasKid = kid != null && kid.isTextual() && !kid.textValue().isBlank();
            boolean noKid = kid == null || kid.isNull();
            if ("RS256".equals(alg) && "at+jwt".equals(typ) && hasKid) {
                return Profile.TARGET;
            }
            if ("HS256".equals(alg) && "JWT".equals(typ) && noKid) {
                return Profile.LEGACY;
            }
            throw unauthorized();
        } catch (AuthException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unauthorized();
        }
    }

    private String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw unauthorized();
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw unauthorized();
        }
        return token;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private AuthException unauthorized() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "access token이 올바르지 않습니다.");
    }

    private enum Profile {
        LEGACY,
        TARGET
    }

    public record Subject(String resourceUserId, UUID authUserId) {

        static Subject legacy(String resourceUserId) {
            return new Subject(resourceUserId, null);
        }

        static Subject target(UUID authUserId) {
            return new Subject(null, authUserId);
        }

        boolean target() {
            return authUserId != null;
        }
    }
}
