package com.party.management.controller;

import com.party.management.model.Activity;
import com.party.management.model.Meeting;
import com.party.management.model.Member;
import com.party.management.service.PartyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final PartyService service;

    public ApiController(PartyService service) {
        this.service = service;
    }

    @GetMapping("/members")
    public List<Member> members() {
        return service.members();
    }

    @GetMapping("/activities")
    public List<Activity> activities() {
        return service.activities();
    }

    @GetMapping("/meetings")
    public List<Meeting> meetings() {
        return service.meetings();
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return Map.of("memberCount", service.countMembers(), "reminders", service.reminders());
    }
}
