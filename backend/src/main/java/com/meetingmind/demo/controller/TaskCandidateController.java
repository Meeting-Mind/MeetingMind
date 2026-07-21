package com.meetingmind.demo.controller;

import com.meetingmind.demo.dto.ConfirmTaskCandidateRequest;
import com.meetingmind.demo.dto.ConfirmTaskCandidateResponse;
import com.meetingmind.demo.dto.TaskCandidateGenerationResponse;
import com.meetingmind.demo.dto.TaskCandidateResponse;
import com.meetingmind.demo.dto.TaskCandidatesResponse;
import com.meetingmind.demo.service.TaskCandidateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meetings")
public class TaskCandidateController {

    private final TaskCandidateService taskCandidateService;

    public TaskCandidateController(TaskCandidateService taskCandidateService) {
        this.taskCandidateService = taskCandidateService;
    }

    @PostMapping("/{meetingId}/task-candidates/generate")
    public TaskCandidateGenerationResponse generate(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId
    ) {
        return taskCandidateService.generate(authorizationHeader, meetingId);
    }

    @GetMapping("/{meetingId}/task-candidates")
    public TaskCandidatesResponse list(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId
    ) {
        return taskCandidateService.list(authorizationHeader, meetingId);
    }

    @PostMapping("/{meetingId}/task-candidates/{candidateId}/confirm")
    public ConfirmTaskCandidateResponse confirm(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @PathVariable String candidateId,
            @RequestBody ConfirmTaskCandidateRequest request
    ) {
        return taskCandidateService.confirm(authorizationHeader, meetingId, candidateId, request);
    }

    @PostMapping("/{meetingId}/task-candidates/{candidateId}/dismiss")
    public TaskCandidateResponse dismiss(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @PathVariable String candidateId
    ) {
        return taskCandidateService.dismiss(authorizationHeader, meetingId, candidateId);
    }
}
