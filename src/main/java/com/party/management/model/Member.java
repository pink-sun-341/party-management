package com.party.management.model;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class Member {
    private Long id;
    private String name;
    private String gender;
    private String phone;
    private String status = "正式党员";
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate applicationDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate activistDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate developmentObjectDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate probationaryDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fullMemberDate;

    public Member() {}

    public Member(Long id, String name, String gender, String phone, String status,
                  LocalDate applicationDate, LocalDate activistDate,
                  LocalDate developmentObjectDate, LocalDate probationaryDate,
                  LocalDate fullMemberDate) {
        this.id = id;
        this.name = name;
        this.gender = gender;
        this.phone = phone;
        this.status = status;
        this.applicationDate = applicationDate;
        this.activistDate = activistDate;
        this.developmentObjectDate = developmentObjectDate;
        this.probationaryDate = probationaryDate;
        this.fullMemberDate = fullMemberDate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getApplicationDate() { return applicationDate; }
    public void setApplicationDate(LocalDate value) { this.applicationDate = value; }
    public LocalDate getActivistDate() { return activistDate; }
    public void setActivistDate(LocalDate value) { this.activistDate = value; }
    public LocalDate getDevelopmentObjectDate() { return developmentObjectDate; }
    public void setDevelopmentObjectDate(LocalDate value) { this.developmentObjectDate = value; }
    public LocalDate getProbationaryDate() { return probationaryDate; }
    public void setProbationaryDate(LocalDate value) { this.probationaryDate = value; }
    public LocalDate getFullMemberDate() { return fullMemberDate; }
    public void setFullMemberDate(LocalDate value) { this.fullMemberDate = value; }
}
