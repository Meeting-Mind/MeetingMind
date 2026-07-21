package com.meetingmind.demo.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meetingmind.demo.auth.target.AuthUserMappingStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class CoreAccountWithdrawalServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-21T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void authWithdrawalEventCompletesPreparedOrCancelledReservation() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        CoreAccountWithdrawalService service = service(jdbc);

        assertThatCode(() -> service.completeFromAuthEvent(UUID.randomUUID()))
                .doesNotThrowAnyException();
    }

    @Test
    void authWithdrawalEventIsIdempotentAfterReservationIsCompleted() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.<Class<Boolean>>any(), any()))
                .thenReturn(true);
        CoreAccountWithdrawalService service = service(jdbc);

        assertThatCode(() -> service.completeFromAuthEvent(UUID.randomUUID()))
                .doesNotThrowAnyException();
    }

    @Test
    void authWithdrawalEventFailsClosedWhenNoCoreReservationExists() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.<Class<Boolean>>any(), any()))
                .thenReturn(false);
        CoreAccountWithdrawalService service = service(jdbc);

        assertThatThrownBy(() -> service.completeFromAuthEvent(UUID.randomUUID()))
                .isInstanceOf(AuthException.class)
                .satisfies(exception -> {
                    AuthException authException = (AuthException) exception;
                    org.assertj.core.api.Assertions.assertThat(authException.status()).isEqualTo(HttpStatus.CONFLICT);
                    org.assertj.core.api.Assertions.assertThat(authException.code())
                            .isEqualTo("WITHDRAWAL_RESERVATION_INVALID");
                });
    }

    private CoreAccountWithdrawalService service(JdbcTemplate jdbc) {
        return new CoreAccountWithdrawalService(jdbc, mock(AuthUserMappingStore.class), CLOCK);
    }
}
