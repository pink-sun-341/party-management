package com.party.management.model;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class Meeting {
    private Long id;
    private String title;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate meetingDate;
    private String location;
    private String host;
    private String recorder;
    private String agenda;
    private String discussion;
    private String decisions;

    public Meeting() {}

    public Meeting(Long id, String title, LocalDate meetingDate, String location, String host,
                   String recorder, String agenda, String discussion, String decisions) {
        this.id = id;
        this.title = title;
        this.meetingDate = meetingDate;
        this.location = location;
        this.host = host;
        this.recorder = recorder;
        this.agenda = agenda;
        this.discussion = discussion;
        this.decisions = decisions;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public LocalDate getMeetingDate() { return meetingDate; }
    public String getLocation() { return location; }
    public String getHost() { return host; }
    public String getRecorder() { return recorder; }
    public String getAgenda() { return agenda; }
    public String getDiscussion() { return discussion; }
    public String getDecisions() { return decisions; }
    public void setTitle(String value) { this.title = value; }
    public void setMeetingDate(LocalDate value) { this.meetingDate = value; }
    public void setLocation(String value) { this.location = value; }
    public void setHost(String value) { this.host = value; }
    public void setRecorder(String value) { this.recorder = value; }
    public void setAgenda(String value) { this.agenda = value; }
    public void setDiscussion(String value) { this.discussion = value; }
    public void setDecisions(String value) { this.decisions = value; }
}
