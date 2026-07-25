package com.meetingmind.demo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetingmind.demo.authz.AuthorizationException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * SMK-005: 회의 전용 GUEST가 허용된 회의 밖 Space 데이터에 접근할 수 없는지 검증한다.
 *
 * <p>기존 guest 커버리지(`MeetingAccessPolicyTest`, `SpaceAccessPolicyTest`,
 * `ProjectAiServiceTest` 등)는 모두 `InMemoryWorkspaceStore`와 정책 객체 단위다. 즉 실제
 * `JdbcWorkspaceStore`의 SQL이 guest 음성 경로로 실행된 적이 없다. SQL에서 space 멤버십
 * 조건이 빠지더라도 기존 테스트는 전부 통과하므로, 실 DB 경로를 별도로 고정한다.
 *
 * <p>양성 대조(positive control)를 함께 둔다. GUEST가 자기 회의를 읽을 수 있어야 셋업이
 * 유효하고, 그때 비로소 나머지 거부가 의미를 갖는다. 셋업이 잘못돼 전부 거부되는 상태는
 * 통과로 보이지만 아무것도 증명하지 못한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "CI_POSTGRES_URL", matches = ".+")
@Transactional
class GuestSpaceAclNegativeIntegrationTest {

    @Autowired
    private WorkspaceStore store;

    @Autowired
    private WorkspaceDomainService service;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CI_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CI_POSTGRES_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("CI_POSTGRES_PASSWORD"));
    }

    @Test
    void meetingOnlyGuestCannotReachSpaceWideDataOnRealDatabase() {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        User owner = store.saveUser(user("acl-owner-" + suffix, now));
        User guest = store.saveUser(user("acl-guest-" + suffix, now));

        WorkspaceDomainService.SpaceCreationResult space = service.createSpace(
                owner.id(), "Guest ACL Space", "guest negative path"
        );
        WorkspaceDomainService.MeetingCreationResult invited = service.createMeeting(
                owner.id(),
                space.space().id(),
                "Guest Invited Meeting",
                OffsetDateTime.of(2026, 7, 26, 13, 0, 0, 0, ZoneOffset.UTC),
                List.of()
        );
        WorkspaceDomainService.MeetingCreationResult other = service.createMeeting(
                owner.id(),
                space.space().id(),
                "Guest Excluded Meeting",
                OffsetDateTime.of(2026, 7, 26, 14, 0, 0, 0, ZoneOffset.UTC),
                List.of()
        );

        // Space 멤버로 넣지 않고 회의 참가자로만 등록한다. 이것이 "회의 전용 guest"다.
        service.addMeetingParticipant(
                owner.id(), invited.meeting().id(), guest.id(), "VIEWER", "guest"
        );

        // 양성 대조: 초대된 회의는 읽을 수 있어야 한다.
        assertThat(service.meetingDetail(guest.id(), invited.meeting().id()).meeting().id())
                .as("guest는 초대된 회의를 읽을 수 있어야 한다 (셋업 유효성)")
                .isEqualTo(invited.meeting().id());

        // 같은 Space의 다른 회의는 막혀야 한다.
        assertThatThrownBy(() -> service.meetingDetail(guest.id(), other.meeting().id()))
                .as("guest는 초대되지 않은 같은 Space 회의를 읽을 수 없어야 한다")
                .isInstanceOf(AuthorizationException.class);

        // Space 범위 목록은 모두 막혀야 한다.
        assertThatThrownBy(() -> service.listMeetings(guest.id(), space.space().id()))
                .as("guest는 Space 회의 목록을 볼 수 없어야 한다")
                .isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> service.spaceDetail(guest.id(), space.space().id()))
                .as("guest는 Space 상세를 볼 수 없어야 한다")
                .isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> service.listSpaceMembers(guest.id(), space.space().id()))
                .as("guest는 Space 멤버 목록을 볼 수 없어야 한다")
                .isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> service.listProjectKnowledge(
                guest.id(), space.space().id(), null, null))
                .as("guest는 Space knowledge를 볼 수 없어야 한다")
                .isInstanceOf(AuthorizationException.class);

        // 회의 참가자 등록이 Space 멤버십으로 승격되지 않았음을 저장소 수준에서 확인한다.
        assertThat(store.findSpaceMembers(space.space().id()))
                .as("guest가 Space 멤버로 승격되면 안 된다")
                .extracting(SpaceMember::userId)
                .doesNotContain(guest.id());
    }

    private static User user(String id, Instant now) {
        return new User(id, id + "@meetingmind.test", id, null, "active", now, now);
    }
}
