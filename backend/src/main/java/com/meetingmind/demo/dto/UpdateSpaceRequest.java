package com.meetingmind.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

public class UpdateSpaceRequest {
    private String name;
    private String description;
    private String imageUrl;
    private boolean namePresent;
    private boolean descriptionPresent;
    private boolean imageUrlPresent;

    public String name() { return name; }
    public String description() { return description; }
    public String imageUrl() { return imageUrl; }
    @JsonIgnore public boolean namePresent() { return namePresent; }
    @JsonIgnore public boolean descriptionPresent() { return descriptionPresent; }
    @JsonIgnore public boolean imageUrlPresent() { return imageUrlPresent; }

    @JsonSetter("name")
    public void setName(String value) { name = value; namePresent = true; }

    @JsonSetter("description")
    public void setDescription(String value) { description = value; descriptionPresent = true; }

    @JsonSetter("imageUrl")
    public void setImageUrl(String value) { imageUrl = value; imageUrlPresent = true; }
}
