package com.meetingmind.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class KnowledgeFolderDtos {
    private KnowledgeFolderDtos() {}

    public record Folder(String id, String spaceId, String name, List<String> knowledgeIds, Instant updatedAt) {}
    public record ListResponse(List<Folder> folders) {}
    public record NameRequest(@NotBlank @Size(max = 120) String name) {}
    public record MutationResponse(Folder folder) {}
}
