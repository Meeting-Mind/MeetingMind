package com.meetingmind.demo.service;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.domain.DomainTermCatalogService;
import com.meetingmind.demo.domain.KnowledgeGraphEdgeStore;
import com.meetingmind.demo.dto.KnowledgeGraphResponse;
import com.meetingmind.demo.dto.ai.KnowledgeGraphGatewayRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KnowledgeGraphService {

    private final AuthService authService;
    private final AiSearchScopeResolver scopeResolver;
    private final KnowledgeGraphGatewayClient gatewayClient;
    private final DomainTermCatalogService domainTermCatalogService;
    private final KnowledgeGraphEdgeStore knowledgeGraphEdgeStore;

    public KnowledgeGraphService(
            AuthService authService,
            AiSearchScopeResolver scopeResolver,
            KnowledgeGraphGatewayClient gatewayClient,
            DomainTermCatalogService domainTermCatalogService,
            KnowledgeGraphEdgeStore knowledgeGraphEdgeStore
    ) {
        this.authService = authService;
        this.scopeResolver = scopeResolver;
        this.gatewayClient = gatewayClient;
        this.domainTermCatalogService = domainTermCatalogService;
        this.knowledgeGraphEdgeStore = knowledgeGraphEdgeStore;
    }

    public KnowledgeGraphResponse graph(String authorizationHeader, String spaceId) {
        return graph(authorizationHeader, spaceId, List.of(), List.of());
    }

    public KnowledgeGraphResponse graph(
            String authorizationHeader,
            String spaceId,
            List<String> requestedMeetingIds,
            List<String> requestedNodeTypes
    ) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        AiSearchScopeResolver.ProjectSearchScope scope = scopeResolver.projectScope(user.id(), spaceId);
        Set<String> allowedMeetingIds = new HashSet<>(scope.allowedMeetingIds());
        List<String> meetingIds = requestedMeetingIds == null || requestedMeetingIds.isEmpty()
                ? scope.allowedMeetingIds()
                : requestedMeetingIds.stream().filter(allowedMeetingIds::contains).distinct().toList();
        validateNodeTypes(requestedNodeTypes);
        try {
            KnowledgeGraphResponse response = gatewayClient.graph(
                    new KnowledgeGraphGatewayRequest(scope.spaceId(), meetingIds)
            );
            KnowledgeGraphResponse withGlossary = appendGlossaryNodes(
                    response,
                    domainTermCatalogService.list(user.id(), scope.spaceId(), "ACTIVE", null)
            );
            KnowledgeGraphResponse filtered = filter(withGlossary, requestedMeetingIds, requestedNodeTypes);
            return appendStoredEdges(
                    filtered,
                    knowledgeGraphEdgeStore.findBySpaceId(scope.spaceId())
            );
        } catch (AiGatewayException exception) {
            throw new AuthorizationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "KNOWLEDGE_GRAPH_UNAVAILABLE",
                    "Knowledge graph을 일시적으로 불러올 수 없습니다."
            );
        }
    }

    private static void validateNodeTypes(List<String> nodeTypes) {
        if (nodeTypes == null) return;
        Set<String> supported = Set.of(
                "MEETING", "REPORT", "DECISION", "ACTION", "TASK", "PROJECT_KNOWLEDGE",
                "GLOSSARY", "TOPIC", "PARTICIPANT"
        );
        if (nodeTypes.stream().anyMatch(type -> !supported.contains(type))) {
            throw new AuthorizationException(HttpStatus.BAD_REQUEST, "INVALID_GRAPH_FILTER", "지원하지 않는 그래프 노드 유형입니다.");
        }
    }

    private static KnowledgeGraphResponse filter(
            KnowledgeGraphResponse response,
            List<String> meetingIds,
            List<String> nodeTypes
    ) {
        if ((meetingIds == null || meetingIds.isEmpty()) && (nodeTypes == null || nodeTypes.isEmpty())) return response;
        Set<String> meetingFilter = meetingIds == null ? Set.of() : new HashSet<>(meetingIds);
        Set<String> typeFilter = nodeTypes == null ? Set.of() : new HashSet<>(nodeTypes);
        List<KnowledgeGraphResponse.Cluster> clusters = response.clusters().stream()
                .map(cluster -> {
                    List<KnowledgeGraphResponse.Node> nodes = cluster.nodes().stream()
                            .filter(node -> matches(node, meetingFilter, typeFilter))
                            .toList();
                    return new KnowledgeGraphResponse.Cluster(
                            cluster.id(), cluster.label(), nodes.size(), nodes
                    );
                })
                .filter(cluster -> !cluster.nodes().isEmpty())
                .toList();
        Set<String> visible = clusters.stream().flatMap(cluster -> cluster.nodes().stream()).map(KnowledgeGraphResponse.Node::id).collect(Collectors.toSet());
        List<KnowledgeGraphResponse.Edge> edges = response.edges().stream()
                .filter(edge -> visible.contains(edge.from()) && visible.contains(edge.to()))
                .toList();
        return new KnowledgeGraphResponse(clusters, edges, response.generatedAt());
    }

    private static boolean matches(KnowledgeGraphResponse.Node node, Set<String> meetingIds, Set<String> nodeTypes) {
        boolean meetingMatches = meetingIds.isEmpty() || node.sourceMeetingId() == null || meetingIds.contains(node.sourceMeetingId());
        boolean typeMatches = nodeTypes.isEmpty() || nodeTypes.stream().anyMatch(type -> matchesType(type, node.sourceType()));
        return meetingMatches && typeMatches;
    }

    private static boolean matchesType(String requested, String actual) {
        return switch (requested) {
            case "PROJECT_KNOWLEDGE" -> "projectKnowledge".equals(actual);
            case "MEETING" -> "transcript".equals(actual) || "meetingSummary".equals(actual);
            case "REPORT" -> "report".equals(actual);
            case "DECISION" -> "decision".equals(actual);
            case "ACTION" -> "actionItem".equals(actual);
            case "TASK" -> "task".equals(actual);
            case "GLOSSARY" -> "glossary".equals(actual);
            case "TOPIC" -> "topic".equals(actual);
            case "PARTICIPANT" -> "participant".equals(actual);
            default -> false;
        };
    }

    private static KnowledgeGraphResponse appendGlossaryNodes(
            KnowledgeGraphResponse response,
            List<DomainTermCatalogService.CatalogTerm> terms
    ) {
        Map<String, DomainTermCatalogService.CatalogTerm> termsByNodeId = new LinkedHashMap<>();
        terms.forEach(term -> termsByNodeId.put(glossaryNodeId(term), term));

        Set<String> existingNodeIds = new HashSet<>();
        List<KnowledgeGraphResponse.Cluster> clusters = response.clusters().stream()
                .map(cluster -> new KnowledgeGraphResponse.Cluster(
                        cluster.id(),
                        cluster.label(),
                        cluster.sourceCount(),
                        cluster.nodes().stream()
                                .map(node -> enrichGlossaryNode(node, termsByNodeId))
                                .peek(node -> existingNodeIds.add(node.id()))
                                .toList()
                ))
                .collect(Collectors.toCollection(ArrayList::new));

        List<KnowledgeGraphResponse.Node> glossaryNodes = termsByNodeId.entrySet().stream()
                .filter(entry -> !existingNodeIds.contains(entry.getKey()))
                .map(entry -> toGlossaryNode(entry.getKey(), entry.getValue()))
                .toList();
        if (!glossaryNodes.isEmpty()) {
            clusters.add(new KnowledgeGraphResponse.Cluster(
                    "cluster-glossary-catalog",
                    "용어사전",
                    glossaryNodes.size(),
                    glossaryNodes
            ));
        }
        return new KnowledgeGraphResponse(clusters, response.edges(), response.generatedAt());
    }

    private static KnowledgeGraphResponse.Node enrichGlossaryNode(
            KnowledgeGraphResponse.Node node,
            Map<String, DomainTermCatalogService.CatalogTerm> termsByNodeId
    ) {
        DomainTermCatalogService.CatalogTerm term = termsByNodeId.get(node.id());
        if (!"glossary".equals(node.sourceType()) || term == null) {
            return node;
        }
        return new KnowledgeGraphResponse.Node(
                node.id(), node.sourceType(), term.term(), term.definition(),
                node.sourceMeetingId(), node.embeddingStatus()
        );
    }

    private static KnowledgeGraphResponse.Node toGlossaryNode(
            String nodeId,
            DomainTermCatalogService.CatalogTerm term
    ) {
        return new KnowledgeGraphResponse.Node(
                nodeId,
                "glossary",
                term.term(),
                term.definition(),
                null,
                null
        );
    }

    private static String glossaryNodeId(DomainTermCatalogService.CatalogTerm term) {
        return "glossary:" + term.id();
    }

    private static KnowledgeGraphResponse appendStoredEdges(
            KnowledgeGraphResponse response,
            List<KnowledgeGraphEdgeStore.StoredEdge> storedEdges
    ) {
        if (storedEdges == null || storedEdges.isEmpty()) {
            return response;
        }
        Set<String> visibleNodeIds = response.clusters().stream()
                .flatMap(cluster -> cluster.nodes().stream())
                .map(KnowledgeGraphResponse.Node::id)
                .collect(Collectors.toSet());
        Map<String, KnowledgeGraphResponse.Edge> edgesByPair = new LinkedHashMap<>();
        response.edges().forEach(edge -> edgesByPair.put(edgeKey(edge.from(), edge.to()), edge));
        storedEdges.stream()
                .filter(edge -> visibleNodeIds.contains(edge.fromNodeId()))
                .filter(edge -> visibleNodeIds.contains(edge.toNodeId()))
                .filter(edge -> !edge.fromNodeId().equals(edge.toNodeId()))
                .forEach(edge -> edgesByPair.putIfAbsent(
                        edgeKey(edge.fromNodeId(), edge.toNodeId()),
                        new KnowledgeGraphResponse.Edge(
                                edge.fromNodeId(), edge.toNodeId(), edge.similarity()
                        )
                ));
        return new KnowledgeGraphResponse(
                response.clusters(),
                List.copyOf(edgesByPair.values()),
                response.generatedAt()
        );
    }

    private static String edgeKey(String left, String right) {
        return left.compareTo(right) <= 0 ? left + "\u0000" + right : right + "\u0000" + left;
    }
}
