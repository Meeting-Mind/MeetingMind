package com.meetingmind.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

public class UpdateSpaceRequest {
    private String name;
    private String description;
    private boolean namePresent;
    private boolean descriptionPresent;

    public String name() { return name; }
    public String description() { return description; }
    @JsonIgnore public boolean namePresent() { return namePresent; }
    @JsonIgnore public boolean descriptionPresent() { return descriptionPresent; }

    @JsonSetter("name")
    public void setName(String value) { name = value; namePresent = true; }

    @JsonSetter("description")
    public void setDescription(String value) { description = value; descriptionPresent = true; }
}
