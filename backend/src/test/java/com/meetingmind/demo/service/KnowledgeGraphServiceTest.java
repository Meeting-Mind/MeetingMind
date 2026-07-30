package com.meetingmind.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.domain.DomainTermCatalogService;
import com.meetingmind.demo.domain.DomainTermStatus;
import com.meetingmind.demo.domain.KnowledgeGraphEdgeStore;
import com.meetingmind.demo.dto.KnowledgeGraphResponse;
import com.meetingmind.demo.dto.ai.KnowledgeGraphGatewayRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeGraphServiceTest {

    @Test
    void graphAddsGlossaryNodesWithDefinitionsAndSupportsGlossaryFilter() {
        AuthService authService = mock(AuthService.class);
        AiSearchScopeResolver scopeResolver = mock(AiSearchScopeResolver.class);
        KnowledgeGraphGatewayClient gatewayClient = mock(KnowledgeGraphGatewayClient.class);
        DomainTermCatalogService catalog = mock(DomainTermCatalogService.class);
        KnowledgeGraphEdgeStore edgeStore = mock(KnowledgeGraphEdgeStore.class);
        when(authService.currentUser("Bearer token"))
                .thenReturn(new AuthUserResponse("user-1", "user@example.com", "User", null, "active"));
        when(scopeResolver.projectScope("user-1", "space-1"))
                .thenReturn(new AiSearchScopeResolver.ProjectSearchScope("space-1", List.of("meeting-1")));
        when(gatewayClient.graph(new KnowledgeGraphGatewayRequest("space-1", List.of("meeting-1"))))
                .thenReturn(new KnowledgeGraphResponse(
                        List.of(new KnowledgeGraphResponse.Cluster(
                                "cluster-report",
                                "회의 보고서",
                                1,
                                List.of(new KnowledgeGraphResponse.Node(
                                        "report:report-1", "report", "주간 보고서",
                                        null, "meeting-1", "COMPLETED"
                                ))
                        )),
                        List.of(),
                        "2026-07-27T00:00:00Z"
                ));
        when(catalog.list("user-1", "space-1", "ACTIVE", null)).thenReturn(List.of(
                new DomainTermCatalogService.CatalogTerm(
                        "term-rag", "RAG", "검색 결과를 근거로 답변을 생성하는 방식",
                        DomainTermStatus.ACTIVE, Instant.parse("2026-07-27T00:00:00Z"),
                        DomainTermCatalogService.Source.SPACE, null, null, true
                )
        ));
        when(edgeStore.findBySpaceId("space-1")).thenReturn(List.of());
        KnowledgeGraphService service = new KnowledgeGraphService(
                authService, scopeResolver, gatewayClient, catalog, edgeStore
        );

        KnowledgeGraphResponse response = service.graph(
                "Bearer token", "space-1", List.of(), List.of("GLOSSARY")
        );

        assertThat(response.clusters()).singleElement().satisfies(cluster -> {
            assertThat(cluster.label()).isEqualTo("용어사전");
            assertThat(cluster.sourceCount()).isEqualTo(1);
            assertThat(cluster.nodes()).singleElement().satisfies(node -> {
                assertThat(node.id()).isEqualTo("glossary:term-rag");
                assertThat(node.sourceType()).isEqualTo("glossary");
                assertThat(node.title()).isEqualTo("RAG");
                assertThat(node.description()).isEqualTo("검색 결과를 근거로 답변을 생성하는 방식");
                assertThat(node.embeddingStatus()).isNull();
            });
        });
        assertThat(response.edges()).isEmpty();
    }

    @Test
    void graphMergesStoredEdgesOnlyWhenBothEndpointsRemainVisible() {
        AuthService authService = mock(AuthService.class);
        AiSearchScopeResolver scopeResolver = mock(AiSearchScopeResolver.class);
        KnowledgeGraphGatewayClient gatewayClient = mock(KnowledgeGraphGatewayClient.class);
        DomainTermCatalogService catalog = mock(DomainTermCatalogService.class);
        KnowledgeGraphEdgeStore edgeStore = mock(KnowledgeGraphEdgeStore.class);
        when(authService.currentUser("Bearer token"))
                .thenReturn(new AuthUserResponse("user-1", "user@example.com", "User", null, "active"));
        when(scopeResolver.projectScope("user-1", "space-1"))
                .thenReturn(new AiSearchScopeResolver.ProjectSearchScope("space-1", List.of("meeting-1")));
        when(gatewayClient.graph(new KnowledgeGraphGatewayRequest("space-1", List.of("meeting-1"))))
                .thenReturn(new KnowledgeGraphResponse(
                        List.of(new KnowledgeGraphResponse.Cluster(
                                "cluster-report",
                                "회의 보고서",
                                1,
                                List.of(new KnowledgeGraphResponse.Node(
                                        "report:report-1", "report", "주간 보고서",
                                        null, "meeting-1", "COMPLETED"
                                ))
                        )),
                        List.of(),
                        "2026-07-28T00:00:00Z"
                ));
        when(catalog.list("user-1", "space-1", "ACTIVE", null)).thenReturn(List.of(
                new DomainTermCatalogService.CatalogTerm(
                        "term-rag", "RAG", "검색 결과를 근거로 답변을 생성하는 방식",
                        DomainTermStatus.ACTIVE, Instant.parse("2026-07-28T00:00:00Z"),
                        DomainTermCatalogService.Source.SPACE, null, null, true
                )
        ));
        when(edgeStore.findBySpaceId("space-1")).thenReturn(List.of(
                new KnowledgeGraphEdgeStore.StoredEdge(
                        "edge-1", "glossary:term-rag", "report:report-1", 0.5
                ),
                new KnowledgeGraphEdgeStore.StoredEdge(
                        "edge-hidden", "glossary:term-rag", "report:hidden", 0.5
                )
        ));
        KnowledgeGraphService service = new KnowledgeGraphService(
                authService, scopeResolver, gatewayClient, catalog, edgeStore
        );

        KnowledgeGraphResponse response = service.graph(
                "Bearer token", "space-1", List.of(), List.of()
        );

        assertThat(response.edges()).containsExactly(
                new KnowledgeGraphResponse.Edge("glossary:term-rag", "report:report-1", 0.5)
        );
    }
}
