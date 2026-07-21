package com.meetingmind.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

public class UpdateProjectKnowledgeRequest {
    private String title;
    private String content;
    private boolean titlePresent;
    private boolean contentPresent;

    public String title() { return title; }
    public String content() { return content; }
    @JsonIgnore public boolean titlePresent() { return titlePresent; }
    @JsonIgnore public boolean contentPresent() { return contentPresent; }

    @JsonSetter("title") public void setTitle(String value) { title = value; titlePresent = true; }
    @JsonSetter("content") public void setContent(String value) { content = value; contentPresent = true; }
}
