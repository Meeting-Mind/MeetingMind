package com.meetingmind.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "task_candidates")
public class TaskCandidate {
    @Id String id;
    @Column(name = "meeting_id", nullable = false) String meetingId;
    @Column(nullable = false) String title;
    @Column(name = "assignee_name") String assigneeName;
    @Column(name = "suggested_assignee_id") String suggestedAssigneeId;
    @Column(name = "due_date") LocalDate dueDate;
    @Column(nullable = false) String status;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "source_ids", columnDefinition = "jsonb", nullable = false) List<String> sourceIds;
    @Column(name = "created_by", nullable = false) String createdBy;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "confirmed_at") Instant confirmedAt;

    protected TaskCandidate() {
    }

    public TaskCandidate(String id, String meetingId, String title, String assigneeName, String suggestedAssigneeId,
                         LocalDate dueDate, TaskCandidateStatus status, List<String> sourceIds, String createdBy,
                         Instant createdAt, Instant confirmedAt) {
        this.id = id; this.meetingId = meetingId; this.title = title; this.assigneeName = assigneeName;
        this.suggestedAssigneeId = suggestedAssigneeId; this.dueDate = dueDate; this.status = status.name();
        this.sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds); this.createdBy = createdBy;
        this.createdAt = createdAt; this.confirmedAt = confirmedAt;
    }

    public String id() { return id; } public String meetingId() { return meetingId; } public String title() { return title; }
    public String assigneeName() { return assigneeName; } public String suggestedAssigneeId() { return suggestedAssigneeId; }
    public LocalDate dueDate() { return dueDate; } public TaskCandidateStatus status() { return TaskCandidateStatus.valueOf(status); }
    public List<String> sourceIds() { return List.copyOf(sourceIds); } public String createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; } public Instant confirmedAt() { return confirmedAt; }
    public TaskCandidate confirmed(Instant confirmedAt) { return new TaskCandidate(id, meetingId, title, assigneeName, suggestedAssigneeId, dueDate, TaskCandidateStatus.CONFIRMED, sourceIds, createdBy, createdAt, confirmedAt); }
    public TaskCandidate dismissed() { return new TaskCandidate(id, meetingId, title, assigneeName, suggestedAssigneeId, dueDate, TaskCandidateStatus.DISMISSED, sourceIds, createdBy, createdAt, null); }
    @Override public boolean equals(Object other) { return other instanceof TaskCandidate value && Objects.equals(id, value.id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
