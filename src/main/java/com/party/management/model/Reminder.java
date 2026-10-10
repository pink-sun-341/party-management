package com.party.management.model;

import java.time.LocalDate;

public record Reminder(String memberName, String message, LocalDate dueDate, String level) {}
