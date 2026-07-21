package com.meetingmind.demo.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

class CoreInternalWorkloadVerifierTest {

    private static final String AUTH_PRINCIPAL =
            "spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-auth";

    @Test
    void acceptsTestPrincipalOnlyInLocalLikeProfilesWhenExplicitlyEnabled() {
        MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles("local");
        CoreInternalWorkloadVerifier verifier = new CoreInternalWorkloadVerifier(AUTH_PRINCIPAL, true, local);
        MockHttpServletRequest request = request(AUTH_PRINCIPAL);

        assertThatCode(() -> verifier.requireAuthWorkload(request)).doesNotThrowAnyException();
    }

    @Test
    void rejectsTheTestPrincipalHeaderInProduction() {
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod");
        CoreInternalWorkloadVerifier verifier = new CoreInternalWorkloadVerifier(AUTH_PRINCIPAL, true, production);

        assertThatThrownBy(() -> verifier.requireAuthWorkload(request(AUTH_PRINCIPAL)))
                .isInstanceOf(AuthException.class)
                .hasMessage("허용되지 않은 workload입니다.");
    }

    private MockHttpServletRequest request(String principal) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/core/account-withdrawal/reconcile");
        request.addHeader("X-MeetingMind-Test-Principal", principal);
        return request;
    }
}
