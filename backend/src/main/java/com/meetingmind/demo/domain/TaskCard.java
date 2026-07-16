package com.meetingmind.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

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
    @Column(name = "assignee_id") String assigneeId;
    @Column(name = "due_date") LocalDate dueDate;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    protected TaskCard() { }
    public TaskCard(String id, String spaceId, String meetingId, String sourceCandidateId, String title, String description,
                    TaskCardStatus status, String assigneeId, LocalDate dueDate, Instant createdAt, Instant updatedAt) {
        this.id=id; this.spaceId=spaceId; this.meetingId=meetingId; this.sourceCandidateId=sourceCandidateId; this.title=title;
        this.description=description; this.status=status.name(); this.assigneeId=assigneeId; this.dueDate=dueDate; this.createdAt=createdAt; this.updatedAt=updatedAt;
    }
    public String id(){return id;} public String spaceId(){return spaceId;} public String meetingId(){return meetingId;}
    public String sourceCandidateId(){return sourceCandidateId;} public String title(){return title;} public String description(){return description;}
    public TaskCardStatus status(){return TaskCardStatus.valueOf(status);} public String assigneeId(){return assigneeId;}
    public LocalDate dueDate(){return dueDate;} public Instant createdAt(){return createdAt;} public Instant updatedAt(){return updatedAt;}
    @Override public boolean equals(Object other){return other instanceof TaskCard value && Objects.equals(id,value.id);}
    @Override public int hashCode(){return Objects.hashCode(id);}
}
