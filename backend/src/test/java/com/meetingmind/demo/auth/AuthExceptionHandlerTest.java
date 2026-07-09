package com.meetingmind.demo.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetingmind.demo.authz.AuthorizationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AuthExceptionHandlerTest {

    private final AuthExceptionHandler handler = new AuthExceptionHandler();

    @Test
    void authorizationExceptionUsesCommonErrorBody() {
        var response = handler.handleAuthorizationException(new AuthorizationException(
                HttpStatus.FORBIDDEN,
                "MEETING_ACCESS_DENIED",
                "회의 접근 권한이 없습니다."
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("MEETING_ACCESS_DENIED");
        assertThat(response.getBody().message()).isEqualTo("회의 접근 권한이 없습니다.");
        assertThat(response.getBody().fieldErrors()).isEmpty();
        assertThat(response.getBody().traceId()).isNotBlank();
    }
}
