package com.meetingmind.demo.service;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.dto.KnowledgeGraphResponse;
import com.meetingmind.demo.dto.ai.KnowledgeGraphGatewayRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KnowledgeGraphService {

    private final AuthService authService;
    private final AiSearchScopeResolver scopeResolver;
    private final KnowledgeGraphGatewayClient gatewayClient;

    public KnowledgeGraphService(
            AuthService authService,
            AiSearchScopeResolver scopeResolver,
            KnowledgeGraphGatewayClient gatewayClient
    ) {
        this.authService = authService;
        this.scopeResolver = scopeResolver;
        this.gatewayClient = gatewayClient;
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
            return filter(response, requestedMeetingIds, requestedNodeTypes);
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
                "MEETING", "REPORT", "DECISION", "ACTION", "TASK", "PROJECT_KNOWLEDGE", "TOPIC", "PARTICIPANT"
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
                .map(cluster -> new KnowledgeGraphResponse.Cluster(
                        cluster.id(), cluster.label(), cluster.sourceCount(), cluster.nodes().stream()
                                .filter(node -> matches(node, meetingFilter, typeFilter)).toList()
                ))
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
            case "TOPIC" -> "topic".equals(actual);
            case "PARTICIPANT" -> "participant".equals(actual);
            default -> false;
        };
    }
}
