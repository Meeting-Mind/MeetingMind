package com.meetingmind.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

public class UpdateDomainTermRequest {
    private String term;
    private String definition;
    private String status;
    private boolean termPresent;
    private boolean definitionPresent;
    private boolean statusPresent;

    public String term() { return term; }
    public String definition() { return definition; }
    public String status() { return status; }
    @JsonIgnore public boolean termPresent() { return termPresent; }
    @JsonIgnore public boolean definitionPresent() { return definitionPresent; }
    @JsonIgnore public boolean statusPresent() { return statusPresent; }

    @JsonSetter("term") public void setTerm(String value) { term = value; termPresent = true; }
    @JsonSetter("definition") public void setDefinition(String value) { definition = value; definitionPresent = true; }
    @JsonSetter("status") public void setStatus(String value) { status = value; statusPresent = true; }
}
