package com.meetingmind.demo.controller;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.domain.TaskCard;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.CreateTaskCardRequest;
import com.meetingmind.demo.dto.CreateTaskCardResponse;
import com.meetingmind.demo.dto.DeleteTaskCardResponse;
import com.meetingmind.demo.dto.TaskListResponse;
import com.meetingmind.demo.dto.UpdateTaskCardRequest;
import com.meetingmind.demo.dto.UpdateTaskCardResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/tasks")
public class TaskCardController {

    private final AuthService authService;
    private final WorkspaceDomainService workspaceDomainService;

    public TaskCardController(AuthService authService, WorkspaceDomainService workspaceDomainService) {
        this.authService = authService;
        this.workspaceDomainService = workspaceDomainService;
    }

    @GetMapping
    public TaskListResponse list(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String keyword
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        return new TaskListResponse(workspaceDomainService.listTaskCards(user.id(), spaceId, status, assigneeId, keyword)
                .stream().map(TaskCardController::toResponse).toList());
    }

    @PostMapping
    public CreateTaskCardResponse create(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @Valid @RequestBody CreateTaskCardRequest request
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        TaskCard created = workspaceDomainService.createTaskCard(
                user.id(), spaceId, request.title(), request.description(), request.assigneeId(), request.dueDate(), request.meetingId(),
                request.priority(), request.labels()
        );
        return new CreateTaskCardResponse(created.id(), created.status().name());
    }

    @PatchMapping("/{taskId}")
    public UpdateTaskCardResponse update(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @PathVariable String taskId,
            @RequestBody UpdateTaskCardRequest request
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        TaskCard updated = workspaceDomainService.updateTaskCard(user.id(), spaceId, taskId, new WorkspaceDomainService.TaskCardPatch(
                request.title(), request.titlePresent(), request.description(), request.descriptionPresent(),
                request.assigneeId(), request.assigneeIdPresent(), request.dueDate(), request.dueDatePresent(),
                request.status(), request.statusPresent(), request.priority(), request.priorityPresent(), request.labels(), request.labelsPresent()
        ));
        return new UpdateTaskCardResponse(updated.id(), updated.status().name(), updated.priority().name(), updated.labels(), updated.updatedAt());
    }

    @DeleteMapping("/{taskId}")
    public DeleteTaskCardResponse delete(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @PathVariable String taskId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        return new DeleteTaskCardResponse(workspaceDomainService.deleteTaskCard(user.id(), spaceId, taskId));
    }

    private AuthUserResponse currentUser(String authorizationHeader) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        workspaceDomainService.ensureUser(user.id(), user.email(), user.displayName(), user.pictureUrl(), user.status());
        return user;
    }

    private static TaskListResponse.Task toResponse(WorkspaceDomainService.TaskCardView view) {
        TaskCard task = view.task();
        return new TaskListResponse.Task(
                task.id(), task.spaceId(), view.meetingSourceVisible() ? task.meetingId() : null,
                task.title(), task.description(), task.status().name(), task.priority().name(), task.labels(), task.assigneeId(), task.dueDate(),
                view.meetingSourceVisible() ? task.sourceCandidateId() : null
        );
    }
}
