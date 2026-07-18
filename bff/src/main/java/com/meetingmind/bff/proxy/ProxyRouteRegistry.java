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
    private static final String SPACES = "/api/v1/spaces";
    private static final String SPACE = SPACES + "/" + SPACE_ID;
    private static final String MEETINGS = "/api/v1/meetings";
    private static final String MEETING = MEETINGS + "/" + MEETING_ID;

    private final List<ProxyRoute> routes = List.of(
            route(HttpMethod.GET, SPACES, DownstreamService.CORE),
            route(HttpMethod.POST, SPACES, DownstreamService.CORE),
            route(HttpMethod.GET, SPACE + "/meetings", DownstreamService.CORE),
            route(HttpMethod.POST, SPACE + "/meetings", DownstreamService.CORE),
            route(HttpMethod.GET, SPACE + "/members", DownstreamService.CORE),
            route(HttpMethod.PATCH, SPACE + "/members/" + SPACE_MEMBER_ID, DownstreamService.CORE),
            route(HttpMethod.DELETE, SPACE + "/members/" + SPACE_MEMBER_ID, DownstreamService.CORE),
            route(HttpMethod.POST, SPACE + "/owner-transfer", DownstreamService.CORE),
            route(HttpMethod.GET, SPACE + "/project-ai/context-candidates", DownstreamService.CORE),
            route(HttpMethod.POST, SPACE + "/ai/chat", DownstreamService.AI),
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
            route(HttpMethod.GET, MEETING + "/task-candidates", DownstreamService.CORE),
            route(HttpMethod.POST, MEETING + "/task-candidates/" + TASK_CANDIDATE_ID + "/confirm", DownstreamService.CORE),
            route(HttpMethod.POST, MEETING + "/reports/" + REPORT_ID + "/confirm", DownstreamService.CORE),
            route(HttpMethod.POST, MEETING + "/ai/chat", DownstreamService.AI),
            route(HttpMethod.POST, MEETING + "/reports/generate", DownstreamService.AI),
            route(HttpMethod.POST, MEETING + "/task-candidates/generate", DownstreamService.AI),
            route(HttpMethod.POST, MEETING + "/livekit-token", DownstreamService.LIVEKIT),
            route(HttpMethod.POST, MEETING + "/transcription/start", DownstreamService.LIVEKIT),
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
