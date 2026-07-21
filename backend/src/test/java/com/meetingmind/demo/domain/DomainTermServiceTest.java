package com.meetingmind.demo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.authz.SpaceAccessPolicy;
import com.meetingmind.demo.authz.SpaceRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DomainTermServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void managerCreatesUpdatesArchivesAndRestoresTerm() {
        TestContext context = newContext();
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace("owner", "MeetingMind", null);
        DomainTerm created = context.terms.create("owner", space.space().id(), "pgvector", "벡터 유사도 검색 확장");

        DomainTerm updated = context.terms.update(
                "owner", space.space().id(), created.id(), null, false,
                "PostgreSQL 벡터 검색 확장", true, "ARCHIVED", true
        );
        DomainTerm restored = context.terms.update(
                "owner", space.space().id(), created.id(), "pgvector", true,
                null, false, "ACTIVE", true
        );

        assertThat(updated.status()).isEqualTo(DomainTermStatus.ARCHIVED);
        assertThat(updated.archivedAt()).isEqualTo(CLOCK.instant());
        assertThat(restored.status()).isEqualTo(DomainTermStatus.ACTIVE);
        assertThat(restored.archivedAt()).isNull();
        assertThat(context.terms.list("owner", space.space().id(), "ACTIVE", "vector"))
                .extracting(DomainTerm::id).containsExactly(created.id());
    }

    @Test
    void memberCanReadButCannotManageAndActiveTermMustBeUniqueIgnoringCase() {
        TestContext context = newContext();
        WorkspaceDomainService.SpaceCreationResult space = context.workspace.createSpace("owner", "MeetingMind", null);
        context.workspaceStore.addSpaceMember(space.space().id(), "member", SpaceRole.MEMBER, CLOCK.instant());
        context.terms.create("owner", space.space().id(), "pgvector", "벡터 검색 확장");

        assertThat(context.terms.list("member", space.space().id(), null, null)).hasSize(1);
        assertThatThrownBy(() -> context.terms.create("member", space.space().id(), "RAG", "검색 방식"))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthorization((AuthorizationException) error, HttpStatus.FORBIDDEN, "SPACE_ACCESS_DENIED"));
        assertThatThrownBy(() -> context.terms.create("owner", space.space().id(), "PGVECTOR", "중복"))
                .isInstanceOf(AuthorizationException.class)
                .satisfies(error -> assertAuthorization((AuthorizationException) error, HttpStatus.CONFLICT, "INVALID_REQUEST"));
    }

    private static void assertAuthorization(AuthorizationException exception, HttpStatus status, String code) {
        assertThat(exception.status()).isEqualTo(status);
        assertThat(exception.code()).isEqualTo(code);
    }

    private TestContext newContext() {
        InMemoryWorkspaceStore workspaceStore = new InMemoryWorkspaceStore();
        workspaceStore.saveUser(user("owner", "owner@meetingmind.test"));
        workspaceStore.saveUser(user("member", "member@meetingmind.test"));
        SpaceAccessPolicy policy = new SpaceAccessPolicy();
        WorkspaceDomainService workspace = new WorkspaceDomainService(workspaceStore, policy, CLOCK);
        return new TestContext(workspaceStore, workspace, new DomainTermService(
                new InMemoryDomainTermStore(), workspace, policy, CLOCK
        ));
    }

    private static User user(String id, String email) {
        return new User(id, email, id, null, "active", CLOCK.instant(), CLOCK.instant());
    }

    private record TestContext(
            InMemoryWorkspaceStore workspaceStore,
            WorkspaceDomainService workspace,
            DomainTermService terms
    ) {
    }
}
