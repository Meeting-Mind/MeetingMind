package com.meetingmind.demo.service;

import com.meetingmind.demo.dto.KnowledgeFolderDtos;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KnowledgeFolderService {
    private final JdbcTemplate jdbc;

    public KnowledgeFolderService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<KnowledgeFolderDtos.Folder> list(String actorUserId, String spaceId) {
        requireSpaceMember(actorUserId, spaceId, false);
        return jdbc.query("select id, space_id, name, updated_at from knowledge_folders where space_id = ? order by name", (rs, row) ->
                folder(rs.getString("id"), rs.getString("space_id"), rs.getString("name"), rs.getTimestamp("updated_at").toInstant()), spaceId)
                .stream().map(value -> withNodes(value.id(), value.spaceId(), value.name(), value.updatedAt())).toList();
    }

    public KnowledgeFolderDtos.Folder create(String actorUserId, String spaceId, String name) {
        requireSpaceMember(actorUserId, spaceId, true);
        String id = "knowledge-folder-" + UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("insert into knowledge_folders(id, space_id, name, created_by, created_at, updated_at) values (?, ?, ?, ?, ?, ?)", id, spaceId, name.trim(), actorUserId, now, now);
        return withNodes(id, spaceId, name.trim(), now);
    }

    public KnowledgeFolderDtos.Folder rename(String actorUserId, String spaceId, String folderId, String name) {
        requireSpaceMember(actorUserId, spaceId, true);
        Instant now = Instant.now();
        int updated = jdbc.update("update knowledge_folders set name = ?, updated_at = ? where id = ? and space_id = ?", name.trim(), now, folderId, spaceId);
        if (updated == 0) throw notFound("Knowledge folder not found.");
        return find(actorUserId, spaceId, folderId);
    }

    public KnowledgeFolderDtos.Folder assign(String actorUserId, String spaceId, String folderId, String knowledgeId) {
        requireSpaceMember(actorUserId, spaceId, true);
        if (jdbc.queryForObject("select count(*) from project_knowledge where id = ? and space_id = ? and status = 'PUBLISHED'", Integer.class, knowledgeId, spaceId) == 0) throw notFound("Project knowledge not found.");
        jdbc.update("insert into knowledge_folder_nodes(folder_id, knowledge_id) values (?, ?) on conflict do nothing", folderId, knowledgeId);
        return find(actorUserId, spaceId, folderId);
    }

    public void remove(String actorUserId, String spaceId, String folderId, String knowledgeId) {
        requireSpaceMember(actorUserId, spaceId, true);
        jdbc.update("delete from knowledge_folder_nodes where folder_id = ? and knowledge_id = ?", folderId, knowledgeId);
    }

    public void delete(String actorUserId, String spaceId, String folderId) {
        requireSpaceMember(actorUserId, spaceId, true);
        if (jdbc.update("delete from knowledge_folders where id = ? and space_id = ?", folderId, spaceId) == 0) throw notFound("Knowledge folder not found.");
    }

    private KnowledgeFolderDtos.Folder find(String actorUserId, String spaceId, String folderId) {
        requireSpaceMember(actorUserId, spaceId, false);
        return jdbc.query("select id, space_id, name, updated_at from knowledge_folders where id = ? and space_id = ?", (rs, row) -> folder(rs.getString("id"), rs.getString("space_id"), rs.getString("name"), rs.getTimestamp("updated_at").toInstant()), folderId, spaceId)
                .stream().findFirst().map(value -> withNodes(value.id(), value.spaceId(), value.name(), value.updatedAt())).orElseThrow(() -> notFound("Knowledge folder not found."));
    }

    private KnowledgeFolderDtos.Folder withNodes(String id, String spaceId, String name, Instant updatedAt) {
        List<String> ids = jdbc.queryForList("select knowledge_id from knowledge_folder_nodes where folder_id = ? order by created_at", String.class, id);
        return new KnowledgeFolderDtos.Folder(id, spaceId, name, ids, updatedAt);
    }

    private KnowledgeFolderDtos.Folder folder(String id, String spaceId, String name, Instant updatedAt) { return new KnowledgeFolderDtos.Folder(id, spaceId, name, List.of(), updatedAt); }

    private void requireSpaceMember(String userId, String spaceId, boolean manage) {
        String role = jdbc.query("select role from space_members where space_id = ? and user_id = ? and removed_at is null", (rs, row) -> rs.getString(1), spaceId, userId).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Space access denied."));
        if (manage && !role.equals("OWNER") && !role.equals("ADMIN")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Knowledge folder management requires OWNER or ADMIN.");
    }

    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
