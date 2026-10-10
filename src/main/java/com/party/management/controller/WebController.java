package com.party.management.controller;

import com.party.management.model.Activity;
import com.party.management.model.Meeting;
import com.party.management.model.Member;
import com.party.management.service.PartyService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
public class WebController {
    private final PartyService service;

    public WebController(PartyService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("memberCount", service.countMembers());
        model.addAttribute("members", service.members());
        model.addAttribute("reminders", service.reminders());
        model.addAttribute("meetings", service.meetings().stream().limit(5).toList());
        return "dashboard";
    }

    @GetMapping("/members")
    public String members(@RequestParam(required = false) String keyword,
                          @RequestParam(required = false) String status,
                          Model model) {
        model.addAttribute("members", service.members(keyword, status));
        model.addAttribute("member", new Member());
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("selectedStatus", status == null ? "" : status);
        model.addAttribute("editMode", false);
        model.addAttribute("statusOptions", memberStatusOptions());
        return "members";
    }

    @PostMapping("/members")
    public String addMember(@ModelAttribute Member member) {
        service.addMember(member);
        return "redirect:/members";
    }

    @GetMapping("/members/{id}/edit")
    public String editMember(@PathVariable long id, Model model) {
        Member member = service.member(id);
        if (member == null) {
            return "redirect:/members";
        }
        model.addAttribute("members", service.members());
        model.addAttribute("member", member);
        model.addAttribute("keyword", "");
        model.addAttribute("selectedStatus", "");
        model.addAttribute("editMode", true);
        model.addAttribute("statusOptions", memberStatusOptions());
        return "members";
    }

    private List<String> memberStatusOptions() {
        return List.of("正式党员", "预备党员", "发展对象", "积极分子", "入党申请人");
    }

    @PostMapping("/members/{id}")
    public String updateMember(@PathVariable long id, @ModelAttribute Member member) {
        member.setId(id);
        service.updateMember(member);
        return "redirect:/members";
    }

    @PostMapping("/members/{id}/delete")
    public String deleteMember(@PathVariable long id) {
        service.deleteMember(id);
        return "redirect:/members";
    }

    @GetMapping("/activities")
    public String activities(Model model) {
        model.addAttribute("activities", service.activities());
        model.addAttribute("activity", new Activity());
        return "activities";
    }

    @PostMapping("/activities")
    public String addActivity(@ModelAttribute Activity activity) {
        service.addActivity(activity);
        return "redirect:/activities";
    }

    @GetMapping("/meetings")
    public String meetings(Model model) {
        model.addAttribute("meetings", service.meetings());
        model.addAttribute("meeting", new Meeting());
        return "meetings";
    }

    @PostMapping("/meetings")
    public String addMeeting(@ModelAttribute Meeting meeting) {
        service.addMeeting(meeting);
        return "redirect:/meetings";
    }

    @GetMapping("/meetings/{id}/minutes.docx")
    public ResponseEntity<ByteArrayResource> downloadMinutes(@PathVariable long id) throws IOException {
        byte[] content = service.generateMinutes(id);
        String filename = URLEncoder.encode("会议纪要.docx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .contentLength(content.length)
                .body(new ByteArrayResource(content));
    }
}
