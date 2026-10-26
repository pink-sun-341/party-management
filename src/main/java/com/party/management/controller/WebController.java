package com.party.management.controller;

import com.party.management.model.Activity;
import com.party.management.model.ActivityFile;
import com.party.management.model.Meeting;
import com.party.management.model.Member;
import com.party.management.service.PartyService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.multipart.MultipartFile;

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
    public String members(@RequestParam(name = "keyword", required = false) String keyword,
                          @RequestParam(name = "status", required = false) String status,
                          @RequestParam(name = "page", defaultValue = "1") int page,
                          Model model) {
        final int pageSize = 10;
        String selectedKeyword = keyword == null ? "" : keyword.trim();
        String selectedStatus = status == null ? "" : status.trim();
        long resultTotal = service.countMembers(selectedKeyword, selectedStatus);
        int totalPages = Math.max(1, (int) ((resultTotal + pageSize - 1) / pageSize));
        int currentPage = Math.min(Math.max(1, page), totalPages);
        List<Member> pageMembers = service.members(selectedKeyword, selectedStatus, currentPage, pageSize);
        int pageStart = resultTotal == 0 ? 0 : (currentPage - 1) * pageSize + 1;
        int pageEnd = Math.min(currentPage * pageSize, (int) resultTotal);
        model.addAttribute("members", pageMembers);
        model.addAttribute("memberTotal", service.countMembers());
        model.addAttribute("resultTotal", resultTotal);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("pageStart", pageStart);
        model.addAttribute("pageEnd", pageEnd);
        model.addAttribute("member", new Member());
        model.addAttribute("keyword", selectedKeyword);
        model.addAttribute("selectedStatus", selectedStatus);
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
    public String editMember(@PathVariable("id") long id, Model model) {
        Member member = service.member(id);
        if (member == null) {
            return "redirect:/members";
        }
        model.addAttribute("members", service.members());
        long memberTotal = service.countMembers();
        model.addAttribute("memberTotal", memberTotal);
        model.addAttribute("resultTotal", memberTotal);
        model.addAttribute("currentPage", 1);
        model.addAttribute("totalPages", 1);
        model.addAttribute("pageSize", memberTotal);
        model.addAttribute("pageStart", memberTotal == 0 ? 0 : 1);
        model.addAttribute("pageEnd", memberTotal);
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
    public String updateMember(@PathVariable("id") long id, @ModelAttribute Member member) {
        member.setId(id);
        service.updateMember(member);
        return "redirect:/members";
    }

    @PostMapping("/members/{id}/delete")
    public String deleteMember(@PathVariable("id") long id) {
        service.deleteMember(id);
        return "redirect:/members";
    }

    @GetMapping("/activities")
    public String activities(@RequestParam(name = "startDate", required = false) String startDate,
                             @RequestParam(name = "endDate", required = false) String endDate,
                             @RequestParam(name = "type", required = false) String type,
                             Model model) {
        model.addAttribute("activities", service.activities(startDate, endDate, type));
        model.addAttribute("activity", new Activity());
        model.addAttribute("members", service.members());
        model.addAttribute("startDate", startDate == null ? "" : startDate);
        model.addAttribute("endDate", endDate == null ? "" : endDate);
        model.addAttribute("selectedType", type == null ? "" : type);
        model.addAttribute("activityTypes", activityTypes());
        return "activities";
    }

    @PostMapping("/activities")
    public String addActivity(@ModelAttribute Activity activity) {
        service.addActivity(activity);
        return "redirect:/activities";
    }

    @PostMapping("/api/activities")
    @ResponseBody
    public ResponseEntity<?> createActivity(@ModelAttribute Activity activity) {
        try {
            return ResponseEntity.ok(java.util.Map.of("id", service.addActivity(activity)));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "活动保存失败"));
        }
    }

    @PostMapping("/activities/{id}")
    public String updateActivity(@PathVariable("id") long id, @ModelAttribute Activity activity) {
        activity.setId(id);
        service.updateActivity(activity);
        return "redirect:/activities";
    }

    @PutMapping("/api/activities/{id}")
    @ResponseBody
    public ResponseEntity<?> updateActivityApi(@PathVariable("id") long id, @ModelAttribute Activity activity) {
        activity.setId(id);
        return service.updateActivity(activity)
                ? ResponseEntity.ok(java.util.Map.of("id", id))
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/activities/{id}/delete")
    public String deleteActivity(@PathVariable("id") long id) {
        service.deleteActivity(id);
        return "redirect:/activities";
    }

    @DeleteMapping("/api/activities/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteActivityApi(@PathVariable("id") long id) {
        try {
            return service.deleteActivity(id)
                    ? ResponseEntity.noContent().build()
                    : ResponseEntity.notFound().build();
        } catch (RuntimeException ex) {
            return ResponseEntity.internalServerError().body(java.util.Map.of("message", "活动删除失败，请检查数据库文件权限后重试"));
        }
    }

    @GetMapping("/activities/export.csv")
    public ResponseEntity<ByteArrayResource> exportActivities(@RequestParam(name = "startDate", required = false) String startDate,
                                                               @RequestParam(name = "endDate", required = false) String endDate,
                                                               @RequestParam(name = "type", required = false) String type) {
        byte[] content = service.exportActivitiesCsv(service.activities(startDate, endDate, type));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + URLEncoder.encode("组织生活活动.csv", StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .contentLength(content.length)
                .body(new ByteArrayResource(content));
    }

    @GetMapping("/activities/{id}/participants")
    @ResponseBody
    public List<Member> activityParticipants(@PathVariable("id") long id) {
        return service.activityParticipants(id);
    }

    @GetMapping("/activities/{id}/images")
    @ResponseBody
    public List<ActivityFile> activityImages(@PathVariable("id") long id) {
        return service.activityImages(id);
    }

    @PostMapping("/activities/{id}/images")
    @ResponseBody
    public ResponseEntity<?> uploadActivityImages(@PathVariable("id") long id,
                                                  @RequestParam(name = "files") List<MultipartFile> files) {
        try {
            return ResponseEntity.ok(service.saveActivityImages(id, files));
        } catch (IOException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage() == null ? "活动图片上传失败" : ex.getMessage()));
        }
    }

    @GetMapping("/activities/files/{fileId}")
    public ResponseEntity<Resource> activityFile(@PathVariable("fileId") long fileId) {
        Resource resource = service.activityFile(fileId);
        String fileType = service.activityFileContentType(fileId);
        if (resource == null || fileType == null) return ResponseEntity.notFound().build();
        String contentType = fileType.isBlank() ? "application/octet-stream" : fileType;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    @DeleteMapping("/activities/files/{fileId}")
    @ResponseBody
    public ResponseEntity<?> deleteActivityFile(@PathVariable("fileId") long fileId) throws IOException {
        return service.deleteActivityFile(fileId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/activities/{id}/participants")
    @ResponseBody
    public ResponseEntity<?> replaceParticipants(@PathVariable("id") long id,
                                                  @RequestParam(name = "memberIds", required = false) List<Long> memberIds) {
        service.replaceActivityParticipants(id, memberIds);
        return ResponseEntity.ok(java.util.Map.of("participantCount", service.activityParticipants(id).size()));
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

    private List<String> activityTypes() {
        return List.of("支部党员大会", "支部委员会", "党小组会", "党课", "主题党日", "其他活动");
    }

    @GetMapping("/meetings/{id}/minutes.docx")
    public ResponseEntity<ByteArrayResource> downloadMinutes(@PathVariable("id") long id) throws IOException {
        byte[] content = service.generateMinutes(id);
        String filename = URLEncoder.encode("会议纪要.docx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .contentLength(content.length)
                .body(new ByteArrayResource(content));
    }
}
