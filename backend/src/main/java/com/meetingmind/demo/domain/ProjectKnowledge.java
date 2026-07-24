package com.meetingmind.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "project_knowledge")
public class ProjectKnowledge {
    @Id String id;
    @Column(name = "space_id", nullable = false) String spaceId;
    @Column(nullable = false) String type;
    @Column(nullable = false) String title;
    @Column(nullable = false) String content;
    @Column(name = "source_meeting_id") String sourceMeetingId;
    @Column(name = "approved_by") String approvedBy;
    @Column(nullable = false) String status;
    @Column(name = "embedding_status", nullable = false) String embeddingStatus;
    @Column(name = "embedding_job_id") String embeddingJobId;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "deleted_at") Instant deletedAt;
    protected ProjectKnowledge() { }
    public ProjectKnowledge(String id, String spaceId, KnowledgeType type, String title, String content, String sourceMeetingId,
                            String approvedBy, KnowledgeStatus status, EmbeddingStatus embeddingStatus, String embeddingJobId,
                            Instant createdAt, Instant updatedAt, Instant deletedAt) {
        this.id=id; this.spaceId=spaceId; this.type=type.name().toLowerCase(); this.title=title; this.content=content; this.sourceMeetingId=sourceMeetingId;
        this.approvedBy=approvedBy; this.status=status.name(); this.embeddingStatus=embeddingStatus.name(); this.embeddingJobId=embeddingJobId;
        this.createdAt=createdAt; this.updatedAt=updatedAt; this.deletedAt=deletedAt;
    }
    public String id(){return id;} public String spaceId(){return spaceId;} public KnowledgeType type(){return KnowledgeType.valueOf(type.toUpperCase());}
    public String title(){return title;} public String content(){return content;} public String sourceMeetingId(){return sourceMeetingId;}
    public String approvedBy(){return approvedBy;} public KnowledgeStatus status(){return KnowledgeStatus.valueOf(status);}
    public EmbeddingStatus embeddingStatus(){return EmbeddingStatus.valueOf(embeddingStatus);} public String embeddingJobId(){return embeddingJobId;}
    public Instant createdAt(){return createdAt;} public Instant updatedAt(){return updatedAt;} public Instant deletedAt(){return deletedAt;}
    public ProjectKnowledge updated(String title, String content, Instant updatedAt) {
        return new ProjectKnowledge(
                id, spaceId, type(), title, content, sourceMeetingId, approvedBy, KnowledgeStatus.PUBLISHED,
                EmbeddingStatus.PENDING, null, createdAt, updatedAt, null
        );
    }
    public ProjectKnowledge archived(Instant archivedAt) {
        return new ProjectKnowledge(
                id, spaceId, type(), title, content, sourceMeetingId, approvedBy, KnowledgeStatus.ARCHIVED,
                embeddingStatus(), embeddingJobId, createdAt, archivedAt, archivedAt
        );
    }
    public ProjectKnowledge restored(Instant restoredAt) {
        return new ProjectKnowledge(
                id, spaceId, type(), title, content, sourceMeetingId, approvedBy, KnowledgeStatus.PUBLISHED,
                EmbeddingStatus.PENDING, null, createdAt, restoredAt, null
        );
    }
    @Override public boolean equals(Object other){return other instanceof ProjectKnowledge value && Objects.equals(id,value.id);}
    @Override public int hashCode(){return Objects.hashCode(id);}
}
