package com.meetingmind.demo.auth;

import com.meetingmind.demo.auth.target.AuthUserMappingStore;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.domain.OwnerAssignmentGuard;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Core owns the owner guard and delayed PII anonymization; Auth never reads this state. */
@Service
@Profile({"local", "db"})
public class CoreAccountWithdrawalService implements OwnerAssignmentGuard {

    private static final Duration PREPARE_TTL = Duration.ofMinutes(10);
    private static final Duration ANONYMIZATION_DELAY = Duration.ofDays(30);
    private static final String ANONYMIZED_DISPLAY_NAME = "탈퇴 사용자";

    private final JdbcTemplate jdbc;
    private final AuthUserMappingStore mappings;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public CoreAccountWithdrawalService(
            JdbcTemplate jdbc,
            AuthUserMappingStore mappings,
            ObjectProvider<Clock> clockProvider
    ) {
        this(jdbc, mappings, clockProvider.getIfAvailable(Clock::systemUTC));
    }

    CoreAccountWithdrawalService(JdbcTemplate jdbc, AuthUserMappingStore mappings, Clock clock) {
        this.jdbc = jdbc;
        this.mappings = mappings;
        this.clock = clock;
    }

    @Transactional
    public void prepare(UUID authUserId) {
        String coreUserId = mappings.findCoreUserId(authUserId).orElseThrow(this::mappingUnavailable);
        Instant now = Instant.now(clock);
        jdbc.update("""
                update account_withdrawal_reservations
                set status = 'CANCELLED', cancelled_at = ?
                where auth_user_id = ? and status = 'PREPARED' and expires_at <= ?
                """, timestamp(now), authUserId, timestamp(now));

        List<SpaceBlocker> blockers = jdbc.query("""
                select s.id, s.name
                from space_members sm
                join spaces s on s.id = sm.space_id
                where sm.user_id = ?
                  and sm.role = 'OWNER'
                  and sm.removed_at is null
                  and s.deleted_at is null
                  and not exists (
                      select 1
                      from space_members other_owner
                      where other_owner.space_id = sm.space_id
                        and other_owner.user_id <> sm.user_id
                        and other_owner.role = 'OWNER'
                        and other_owner.removed_at is null
                  )
                order by s.id
                for update of s
                """, (rows, rowNumber) -> new SpaceBlocker(rows.getString("id"), rows.getString("name")), coreUserId);
        if (!blockers.isEmpty()) {
            throw new AuthorizationException(
                    HttpStatus.CONFLICT,
                    "SPACE_OWNER_TRANSFER_REQUIRED",
                    "단독 OWNER인 활성 Space의 권한을 이양하거나 Space를 삭제해야 합니다.");
        }
        jdbc.update("""
                insert into account_withdrawal_reservations (
                    auth_user_id, core_user_id, status, prepared_at, expires_at
                ) values (?, ?, 'PREPARED', ?, ?)
                on conflict (auth_user_id) do update
                set core_user_id = excluded.core_user_id,
                    status = 'PREPARED',
                    prepared_at = excluded.prepared_at,
                    expires_at = excluded.expires_at,
                    completed_at = null,
                    anonymize_at = null,
                    anonymized_at = null,
                    cancelled_at = null
                """, authUserId, coreUserId, timestamp(now), timestamp(now.plus(PREPARE_TTL)));
    }

    @Transactional
    public void complete(UUID authUserId) {
        Instant now = Instant.now(clock);
        int updated = jdbc.update("""
                update account_withdrawal_reservations
                set status = 'COMPLETED', completed_at = ?, anonymize_at = ?, cancelled_at = null
                where auth_user_id = ? and status = 'PREPARED' and expires_at > ?
                """, timestamp(now), timestamp(now.plus(ANONYMIZATION_DELAY)), authUserId, timestamp(now));
        if (updated == 0) {
            throw new AuthException(HttpStatus.CONFLICT, "WITHDRAWAL_RESERVATION_INVALID", "탈퇴 예약을 완료할 수 없습니다.");
        }
    }

    /**
     * A durable Auth withdrawal event is authoritative when the BFF lost the normal completion response.
     * This path is intentionally idempotent and can recover an expired or cancelled preparation.
     */
    @Transactional
    public void completeFromAuthEvent(UUID authUserId) {
        Instant now = Instant.now(clock);
        int updated = jdbc.update("""
                update account_withdrawal_reservations
                set status = 'COMPLETED',
                    completed_at = coalesce(completed_at, ?),
                    anonymize_at = coalesce(anonymize_at, ?),
                    cancelled_at = null
                where auth_user_id = ?
                  and status in ('PREPARED', 'CANCELLED')
                """, timestamp(now), timestamp(now.plus(ANONYMIZATION_DELAY)), authUserId);
        if (updated > 0) {
            return;
        }
        Boolean completed = jdbc.queryForObject("""
                select exists(
                    select 1 from account_withdrawal_reservations
                    where auth_user_id = ? and status = 'COMPLETED'
                )
                """, Boolean.class, authUserId);
        if (!Boolean.TRUE.equals(completed)) {
            throw new AuthException(HttpStatus.CONFLICT, "WITHDRAWAL_RESERVATION_INVALID", "탈퇴 예약을 완료할 수 없습니다.");
        }
    }

    @Transactional
    public void cancel(UUID authUserId) {
        Instant now = Instant.now(clock);
        jdbc.update("""
                update account_withdrawal_reservations
                set status = 'CANCELLED', cancelled_at = ?
                where auth_user_id = ? and status = 'PREPARED'
                """, timestamp(now), authUserId);
    }

    @Override
    public void requireOwnerAssignmentAllowed(String coreUserId) {
        Instant now = Instant.now(clock);
        Boolean pending = jdbc.queryForObject("""
                select exists(
                    select 1 from account_withdrawal_reservations
                    where core_user_id = ? and status = 'PREPARED' and expires_at > ?
                )
                """, Boolean.class, coreUserId, timestamp(now));
        if (Boolean.TRUE.equals(pending)) {
            throw new AuthorizationException(
                    HttpStatus.CONFLICT,
                    "ACCOUNT_WITHDRAWAL_PENDING",
                    "탈퇴 처리 중인 계정에는 OWNER 권한을 부여할 수 없습니다.");
        }
    }

    @Scheduled(fixedDelayString = "${meetingmind.withdrawal-anonymization.fixed-delay:PT1H}")
    @Transactional
    public void anonymizeCompletedAccounts() {
        Instant now = Instant.now(clock);
        List<String> coreUserIds = jdbc.query("""
                select core_user_id
                from account_withdrawal_reservations
                where status = 'COMPLETED' and anonymized_at is null and anonymize_at <= ?
                order by anonymize_at
                limit 100
                for update skip locked
                """, (rows, rowNumber) -> rows.getString(1), timestamp(now));
        for (String coreUserId : coreUserIds) {
            jdbc.update("update users set display_name = ?, picture_url = null where id = ?",
                    ANONYMIZED_DISPLAY_NAME, coreUserId);
            jdbc.update("""
                    update account_withdrawal_reservations
                    set anonymized_at = ?
                    where core_user_id = ? and status = 'COMPLETED' and anonymized_at is null
                    """, timestamp(now), coreUserId);
        }
    }

    private AuthException mappingUnavailable() {
        return new AuthException(HttpStatus.CONFLICT, "AUTH_USER_MAPPING_CONFLICT", "사용자 계정을 안전하게 연결할 수 없습니다.");
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private record SpaceBlocker(String id, String name) {
    }
}
