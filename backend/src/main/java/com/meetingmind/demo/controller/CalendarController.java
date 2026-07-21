package com.meetingmind.demo.controller;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.CalendarEventsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calendar")
public class CalendarController {

    private final AuthService authService;
    private final WorkspaceDomainService workspaceDomainService;

    public CalendarController(AuthService authService, WorkspaceDomainService workspaceDomainService) {
        this.authService = authService;
        this.workspaceDomainService = workspaceDomainService;
    }

    @GetMapping("/events")
    public CalendarEventsResponse listEvents(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String spaceId
    ) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        workspaceDomainService.ensureUser(user.id(), user.email(), user.displayName(), user.pictureUrl(), user.status());
        return new CalendarEventsResponse(workspaceDomainService.listCalendarEvents(user.id(), spaceId, from, to)
                .stream()
                .map(event -> new CalendarEventsResponse.Event(
                        event.meeting().id(),
                        event.meeting().spaceId(),
                        event.meeting().id(),
                        event.meeting().title(),
                        event.meeting().scheduledAt(),
                        event.meeting().scheduledEndAt(),
                        event.meeting().status().name()
                ))
                .toList());
    }
}
