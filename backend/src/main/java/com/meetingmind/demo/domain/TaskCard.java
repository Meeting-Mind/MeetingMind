package com.meetingmind.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "task_cards")
public class TaskCard {
    @Id String id;
    @Column(name = "space_id", nullable = false) String spaceId;
    @Column(name = "meeting_id") String meetingId;
    @Column(name = "source_candidate_id") String sourceCandidateId;
    @Column(nullable = false) String title;
    String description;
    @Column(nullable = false) String status;
    @Column(nullable = false) String priority;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]") String[] labels;
    @Column(name = "assignee_id") String assigneeId;
    @Column(name = "due_date") LocalDate dueDate;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "deleted_at") Instant deletedAt;
    protected TaskCard() { }
    public TaskCard(String id, String spaceId, String meetingId, String sourceCandidateId, String title, String description,
                    TaskCardStatus status, String assigneeId, LocalDate dueDate, Instant createdAt, Instant updatedAt) {
        this(id, spaceId, meetingId, sourceCandidateId, title, description, status, TaskCardPriority.MEDIUM, List.of(), assigneeId, dueDate, createdAt, updatedAt, null);
    }
    public TaskCard(String id, String spaceId, String meetingId, String sourceCandidateId, String title, String description,
                    TaskCardStatus status, String assigneeId, LocalDate dueDate, Instant createdAt, Instant updatedAt, Instant deletedAt) {
        this(id, spaceId, meetingId, sourceCandidateId, title, description, status, TaskCardPriority.MEDIUM, List.of(), assigneeId, dueDate, createdAt, updatedAt, deletedAt);
    }
    public TaskCard(String id, String spaceId, String meetingId, String sourceCandidateId, String title, String description,
                    TaskCardStatus status, TaskCardPriority priority, List<String> labels, String assigneeId, LocalDate dueDate,
                    Instant createdAt, Instant updatedAt, Instant deletedAt) {
        this.id=id; this.spaceId=spaceId; this.meetingId=meetingId; this.sourceCandidateId=sourceCandidateId; this.title=title;
        this.description=description; this.status=status.name(); this.priority=priority.name(); this.labels=labels.toArray(String[]::new);
        this.assigneeId=assigneeId; this.dueDate=dueDate; this.createdAt=createdAt; this.updatedAt=updatedAt; this.deletedAt=deletedAt;
    }
    public String id(){return id;} public String spaceId(){return spaceId;} public String meetingId(){return meetingId;}
    public String sourceCandidateId(){return sourceCandidateId;} public String title(){return title;} public String description(){return description;}
    public TaskCardStatus status(){return TaskCardStatus.valueOf(status);} public String assigneeId(){return assigneeId;}
    public TaskCardPriority priority(){return TaskCardPriority.valueOf(priority);} public List<String> labels(){return labels == null ? List.of() : List.copyOf(Arrays.asList(labels));}
    public LocalDate dueDate(){return dueDate;} public Instant createdAt(){return createdAt;} public Instant updatedAt(){return updatedAt;}
    public Instant deletedAt(){return deletedAt;}
    public TaskCard updated(String nextTitle, String nextDescription, TaskCardStatus nextStatus, TaskCardPriority nextPriority, List<String> nextLabels, String nextAssigneeId, LocalDate nextDueDate, Instant now) {
        return new TaskCard(id, spaceId, meetingId, sourceCandidateId, nextTitle, nextDescription, nextStatus, nextPriority, nextLabels, nextAssigneeId, nextDueDate, createdAt, now, deletedAt);
    }
    public TaskCard deleted(Instant value) {
        return new TaskCard(id, spaceId, meetingId, sourceCandidateId, title, description, status(), priority(), labels(), assigneeId, dueDate, createdAt, updatedAt, value);
    }
    @Override public boolean equals(Object other){return other instanceof TaskCard value && Objects.equals(id,value.id);}
    @Override public int hashCode(){return Objects.hashCode(id);}
}
