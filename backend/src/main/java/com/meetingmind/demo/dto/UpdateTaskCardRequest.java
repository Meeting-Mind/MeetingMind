package com.meetingmind.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.LocalDate;
import java.util.List;

public class UpdateTaskCardRequest {
    private String title;
    private String description;
    private String assigneeId;
    private LocalDate dueDate;
    private String status;
    private String priority;
    private List<String> labels;
    private boolean titlePresent;
    private boolean descriptionPresent;
    private boolean assigneeIdPresent;
    private boolean dueDatePresent;
    private boolean statusPresent;
    private boolean priorityPresent;
    private boolean labelsPresent;

    public String title() { return title; }
    public String description() { return description; }
    public String assigneeId() { return assigneeId; }
    public LocalDate dueDate() { return dueDate; }
    public String status() { return status; }
    public String priority() { return priority; }
    public List<String> labels() { return labels; }
    @JsonIgnore public boolean titlePresent() { return titlePresent; }
    @JsonIgnore public boolean descriptionPresent() { return descriptionPresent; }
    @JsonIgnore public boolean assigneeIdPresent() { return assigneeIdPresent; }
    @JsonIgnore public boolean dueDatePresent() { return dueDatePresent; }
    @JsonIgnore public boolean statusPresent() { return statusPresent; }
    @JsonIgnore public boolean priorityPresent() { return priorityPresent; }
    @JsonIgnore public boolean labelsPresent() { return labelsPresent; }
    @JsonIgnore public boolean hasUpdates() { return titlePresent || descriptionPresent || assigneeIdPresent || dueDatePresent || statusPresent || priorityPresent || labelsPresent; }

    @JsonSetter("title") public void setTitle(String value) { title = value; titlePresent = true; }
    @JsonSetter("description") public void setDescription(String value) { description = value; descriptionPresent = true; }
    @JsonSetter("assigneeId") public void setAssigneeId(String value) { assigneeId = value; assigneeIdPresent = true; }
    @JsonSetter("dueDate") public void setDueDate(LocalDate value) { dueDate = value; dueDatePresent = true; }
    @JsonSetter("status") public void setStatus(String value) { status = value; statusPresent = true; }
    @JsonSetter("priority") public void setPriority(String value) { priority = value; priorityPresent = true; }
    @JsonSetter("labels") public void setLabels(List<String> value) { labels = value; labelsPresent = true; }
}
