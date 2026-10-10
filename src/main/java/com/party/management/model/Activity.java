package com.party.management.model;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class Activity {
    private Long id;
    private String title;
    private String type;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate activityDate;
    private String location;
    private Integer participantCount = 0;

    public Activity() {}

    public Activity(Long id, String title, String type, LocalDate activityDate, String location, Integer participantCount) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.activityDate = activityDate;
        this.location = location;
        this.participantCount = participantCount;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getType() { return type; }
    public LocalDate getActivityDate() { return activityDate; }
    public String getLocation() { return location; }
    public Integer getParticipantCount() { return participantCount; }
    public void setTitle(String value) { this.title = value; }
    public void setType(String value) { this.type = value; }
    public void setActivityDate(LocalDate value) { this.activityDate = value; }
    public void setLocation(String value) { this.location = value; }
    public void setParticipantCount(Integer value) { this.participantCount = value; }
}
