package com.meetingmind.demo.service;

import com.meetingmind.demo.authz.MeetingAccessPolicy;
import com.meetingmind.demo.domain.Meeting;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiSearchScopeResolver {

    private final WorkspaceDomainService workspaceDomainService;
    private final MeetingAccessPolicy meetingAccessPolicy;

    public AiSearchScopeResolver(
            WorkspaceDomainService workspaceDomainService,
            MeetingAccessPolicy meetingAccessPolicy
    ) {
        this.workspaceDomainService = workspaceDomainService;
        this.meetingAccessPolicy = meetingAccessPolicy;
    }

    public MeetingSearchScope meetingScope(String userId, String meetingId) {
        meetingAccessPolicy.requireReadAccess(workspaceDomainService.meetingAccessContext(meetingId, userId));
        Meeting meeting = workspaceDomainService.meetingAiContext(meetingId).meeting();
        return new MeetingSearchScope(meeting.spaceId(), meeting.id());
    }

    public ProjectSearchScope projectScope(String userId, String spaceId) {
        List<String> allowedMeetingIds = workspaceDomainService.projectAiContextCandidates(userId, spaceId)
                .meetings()
                .stream()
                .map(Meeting::id)
                .toList();
        return new ProjectSearchScope(spaceId, allowedMeetingIds);
    }

    public record MeetingSearchScope(String spaceId, String meetingId) {
    }

    public record ProjectSearchScope(String spaceId, List<String> allowedMeetingIds) {
        public ProjectSearchScope {
            allowedMeetingIds = allowedMeetingIds == null ? List.of() : List.copyOf(allowedMeetingIds);
        }
    }
}
