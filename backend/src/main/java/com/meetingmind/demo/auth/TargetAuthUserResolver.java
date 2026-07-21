package com.meetingmind.demo.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.auth.target.AccessTokenValidationException;
import com.meetingmind.demo.auth.target.AuthUserMappingStore;
import com.meetingmind.demo.auth.target.TargetAccessTokenValidator;
import java.util.Base64;
import org.springframework.http.HttpStatus;

public final class TargetAuthUserResolver {

    private final TargetAccessTokenValidator validator;
    private final AuthUserMappingStore mappings;
    private final AuthStore authStore;
    private final ObjectMapper objectMapper;

    public TargetAuthUserResolver(
            TargetAccessTokenValidator validator,
            AuthUserMappingStore mappings,
            AuthStore authStore,
            ObjectMapper objectMapper
    ) {
        this.validator = validator;
        this.mappings = mappings;
        this.authStore = authStore;
        this.objectMapper = objectMapper;
    }

    public boolean supports(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return false;
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            return false;
        }
        try {
            JsonNode header = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            return header.isObject() && "at+jwt".equals(header.path("typ").asText());
        } catch (RuntimeException | java.io.IOException exception) {
            return false;
        }
    }

    public AuthUserResponse resolve(String authorizationHeader) {
        TargetAccessTokenValidator.Principal principal = validate(authorizationHeader);
        String coreUserId = mappings.findCoreUserId(principal.userId()).orElseThrow(this::unauthorized);
        return authStore.findUserById(coreUserId)
                .map(AuthUserResponse::from)
                .orElseThrow(this::unauthorized);
    }

    public TargetAccessTokenValidator.Principal validate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw unauthorized();
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        try {
            return validator.validate(token);
        } catch (AccessTokenValidationException exception) {
            throw unauthorized();
        }
    }

    private AuthException unauthorized() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "사용자를 찾을 수 없습니다.");
    }
}
