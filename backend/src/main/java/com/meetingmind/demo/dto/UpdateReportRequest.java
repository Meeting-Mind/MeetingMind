package com.meetingmind.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

public class UpdateReportRequest {
    private String title;
    private String summary;
    private String markdown;
    private boolean titlePresent;
    private boolean summaryPresent;
    private boolean markdownPresent;

    public String title() { return title; }
    public String summary() { return summary; }
    public String markdown() { return markdown; }
    @JsonIgnore public boolean titlePresent() { return titlePresent; }
    @JsonIgnore public boolean summaryPresent() { return summaryPresent; }
    @JsonIgnore public boolean markdownPresent() { return markdownPresent; }
    @JsonIgnore public boolean hasUpdates() { return titlePresent || summaryPresent || markdownPresent; }

    @JsonSetter("title") public void setTitle(String value) { title = value; titlePresent = true; }
    @JsonSetter("summary") public void setSummary(String value) { summary = value; summaryPresent = true; }
    @JsonSetter("markdown") public void setMarkdown(String value) { markdown = value; markdownPresent = true; }
}
