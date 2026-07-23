package com.meetingmind.bff.proxy;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
public class ProxyRouteRegistry {

    private static final String UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final String SPACE_ID = "space-" + UUID;
    private static final String SPACE_MEMBER_ID = "space-member-" + UUID;
    private static final String MEETING_ID = "meeting-" + UUID;
    private static final String MEETING_PARTICIPANT_ID = "meeting-participant-" + UUID;
    private static final String JOIN_REQUEST_ID = "join-request-" + UUID;
    private static final String REPORT_ID = "report-" + UUID;
    private static final String TASK_CANDIDATE_ID = "task-candidate-" + UUID;
    private static final String TASK_ID = "task-" + UUID;
    private static final String TERM_ID = "term-" + UUID;
    private static final String KNOWLEDGE_ID = "knowledge-" + UUID;
    private static final String INVITATION_ID = "space-invitation-" + UUID;
    private static final String SPACES = "/api/v1/spaces";
    private static final String SPACE = SPACES + "/" + SPACE_ID;
    private static final String TERMS = SPACE + "/terms";
    private static final String TERM = TERMS + "/" + TERM_ID;
    private static final String TASKS = SPACE + "/tasks";
    private static final String TASK = TASKS + "/" + TASK_ID;
    private static final String KNOWLEDGE = SPACE + "/knowledge";
    private static final String KNOWLEDGE_ITEM = KNOWLEDGE + "/" + KNOWLEDGE_ID;
    private static final String INVITATIONS = SPACE + "/invitations";
    private static final String MEETINGS = "/api/v1/meetings";
    private static final String MEETING = MEETINGS + "/" + MEETING_ID;
    private static final String REPORTS = MEETING + "/reports";
    private static final String REPORT = REPORTS + "/" + REPORT_ID;
    private static final String TASK_CANDIDATES = MEETING + "/task-candidates";

    private final List<ProxyRoute> routes = List.of(
            route(HttpMethod.GET, SPACES, DownstreamService.CORE),
            route(HttpMethod.POST, SPACES, DownstreamService.CORE),
            route(HttpMethod.GET, SPACE, DownstreamService.CORE),
            route(HttpMethod.PATCH, SPACE, DownstreamService.CORE),
            route(HttpMethod.DELETE, SPACE, DownstreamService.CORE),
            route(HttpMethod.GET, SPACE + "/meetings", DownstreamService.CORE),
            route(HttpMethod.POST, SPACE + "/meetings", DownstreamService.CORE),
            route(HttpMethod.POST, SPACE + "/instant-meetings", DownstreamService.CORE),
            route(HttpMethod.GET, SPACE + "/members", DownstreamService.CORE),
            route(HttpMethod.POST, INVITATIONS, DownstreamService.CORE),
            route(HttpMethod.POST, INVITATIONS + "/" + INVITATION_ID + "/accept", DownstreamService.CORE),
            route(HttpMethod.POST, INVITATIONS + "/" + INVITATION_ID + "/decline", DownstreamService.CORE),
            route(HttpMethod.PATCH, SPACE + "/members/" + SPACE_MEMBER_ID, DownstreamService.CORE),
            route(HttpMethod.DELETE, SPACE + "/members/" + SPACE_MEMBER_ID, DownstreamService.CORE),
            route(HttpMethod.POST, SPACE + "/owner-transfer", DownstreamService.CORE),
            route(HttpMethod.GET, SPACE + "/project-ai/context-candidates", DownstreamService.CORE),
            route(HttpMethod.GET, TERMS, DownstreamService.CORE),
            route(HttpMethod.POST, TERMS, DownstreamService.CORE),
            route(HttpMethod.PATCH, TERM, DownstreamService.CORE),
            route(HttpMethod.DELETE, TERM, DownstreamService.CORE),
            route(HttpMethod.GET, TASKS, DownstreamService.CORE),
            route(HttpMethod.POST, TASKS, DownstreamService.CORE),
            route(HttpMethod.PATCH, TASK, DownstreamService.CORE),
            route(HttpMethod.DELETE, TASK, DownstreamService.CORE),
            route(HttpMethod.GET, KNOWLEDGE, DownstreamService.CORE),
            route(HttpMethod.POST, KNOWLEDGE, DownstreamService.CORE),
            route(HttpMethod.GET, KNOWLEDGE_ITEM, DownstreamService.CORE),
            route(HttpMethod.PATCH, KNOWLEDGE_ITEM, DownstreamService.CORE),
            route(HttpMethod.DELETE, KNOWLEDGE_ITEM, DownstreamService.CORE),
            route(HttpMethod.POST, SPACE + "/ai/chat", DownstreamService.AI),
            route(HttpMethod.GET, SPACE + "/ai/history", DownstreamService.CORE),
            route(HttpMethod.GET, "/api/v1/dashboard", DownstreamService.CORE),
            route(HttpMethod.GET, "/api/v1/calendar/events", DownstreamService.CORE),
            route(HttpMethod.POST, MEETINGS + "/join-requests", DownstreamService.CORE),
            route(HttpMethod.GET, MEETING, DownstreamService.CORE),
            route(HttpMethod.PATCH, MEETING, DownstreamService.CORE),
            route(HttpMethod.DELETE, MEETING, DownstreamService.CORE),
            route(HttpMethod.GET, MEETING + "/participants", DownstreamService.CORE),
            route(HttpMethod.POST, MEETING + "/participants", DownstreamService.CORE),
            route(HttpMethod.PATCH, MEETING + "/participants/" + MEETING_PARTICIPANT_ID, DownstreamService.CORE),
            route(HttpMethod.GET, MEETING + "/join-requests", DownstreamService.CORE),
            route(HttpMethod.POST, MEETING + "/join-requests/" + JOIN_REQUEST_ID + "/approve", DownstreamService.CORE),
            route(HttpMethod.POST, MEETING + "/join-requests/" + JOIN_REQUEST_ID + "/reject", DownstreamService.CORE),
            route(HttpMethod.GET, MEETING + "/dialogue", DownstreamService.CORE),
            route(HttpMethod.POST, MEETING + "/terms/explain", DownstreamService.CORE),
            route(HttpMethod.GET, TASK_CANDIDATES, DownstreamService.CORE),
            route(HttpMethod.POST, TASK_CANDIDATES + "/" + TASK_CANDIDATE_ID + "/confirm", DownstreamService.CORE),
            route(HttpMethod.POST, TASK_CANDIDATES + "/" + TASK_CANDIDATE_ID + "/dismiss", DownstreamService.CORE),
            route(HttpMethod.GET, REPORTS, DownstreamService.CORE),
            route(HttpMethod.GET, REPORT, DownstreamService.CORE),
            route(HttpMethod.PATCH, REPORT, DownstreamService.CORE),
            route(HttpMethod.POST, REPORT + "/confirm", DownstreamService.CORE),
            route(HttpMethod.POST, REPORT + "/restore", DownstreamService.CORE),
            route(HttpMethod.GET, REPORT + "/download", DownstreamService.CORE),
            route(HttpMethod.POST, MEETING + "/ai/chat", DownstreamService.AI),
            route(HttpMethod.POST, MEETING + "/reports/generate", DownstreamService.AI),
            route(HttpMethod.POST, REPORT + "/ai-edits", DownstreamService.AI),
            route(HttpMethod.POST, MEETING + "/task-candidates/generate", DownstreamService.AI),
            route(HttpMethod.POST, MEETING + "/livekit-token", DownstreamService.LIVEKIT),
            route(HttpMethod.POST, MEETING + "/transcription/start", DownstreamService.LIVEKIT),
            route(HttpMethod.POST, MEETING + "/transcription/stop", DownstreamService.LIVEKIT),
            route(HttpMethod.POST, MEETING + "/transcription/" + UUID + "/stop", DownstreamService.LIVEKIT));

    public Optional<ProxyRoute> resolve(HttpMethod method, String path) {
        if (method == null || !validPath(path)) {
            return Optional.empty();
        }
        return routes.stream().filter(route -> route.matches(method, path)).findFirst();
    }

    private boolean validPath(String path) {
        return path != null
                && path.length() <= 512
                && path.startsWith("/api/v1/")
                && !path.contains("%")
                && !path.contains(";")
                && !path.contains("\\")
                && !path.contains("//");
    }

    private static ProxyRoute route(HttpMethod method, String regex, DownstreamService service) {
        return new ProxyRoute(method, Pattern.compile("^" + regex + "$"), service);
    }
}
