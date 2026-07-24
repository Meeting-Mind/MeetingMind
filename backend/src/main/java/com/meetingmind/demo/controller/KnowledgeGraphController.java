package com.meetingmind.demo.controller;

import com.meetingmind.demo.dto.KnowledgeGraphResponse;
import com.meetingmind.demo.service.KnowledgeGraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@RestController
@RequestMapping("/api/v1/spaces")
public class KnowledgeGraphController {

    private final KnowledgeGraphService knowledgeGraphService;

    public KnowledgeGraphController(KnowledgeGraphService knowledgeGraphService) {
        this.knowledgeGraphService = knowledgeGraphService;
    }

    @GetMapping("/{spaceId}/knowledge/graph")
    public KnowledgeGraphResponse graph(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @RequestParam(required = false) List<String> meetingIds,
            @RequestParam(required = false) List<String> nodeTypes
    ) {
        return knowledgeGraphService.graph(authorizationHeader, spaceId, meetingIds, nodeTypes);
    }
}
