package com.party.management.service;

import com.party.management.model.Activity;
import com.party.management.model.Meeting;
import com.party.management.model.Member;
import com.party.management.model.Reminder;
import com.party.management.repository.PartyRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PartyService {
    private final PartyRepository repository;

    public PartyService(PartyRepository repository) {
        this.repository = repository;
    }

    public long countMembers() { return repository.countMembers(); }
    public List<Member> members() { return repository.findMembers(); }
    public List<Member> members(String keyword, String status) { return repository.findMembers(keyword, status); }
    public Member member(long id) { return repository.findMember(id); }
    public void addMember(Member member) { repository.saveMember(member); }
    public boolean updateMember(Member member) { return repository.updateMember(member); }
    public boolean deleteMember(long id) { return repository.deleteMember(id); }
    public List<Activity> activities() { return repository.findActivities(); }
    public void addActivity(Activity activity) { repository.saveActivity(activity); }
    public List<Meeting> meetings() { return repository.findMeetings(); }
    public void addMeeting(Meeting meeting) { repository.saveMeeting(meeting); }

    public List<Reminder> reminders() {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(90);
        List<Reminder> reminders = new ArrayList<>();
        for (Member member : members()) {
            if ("预备党员".equals(member.getStatus()) && member.getProbationaryDate() != null) {
                LocalDate due = member.getProbationaryDate().plusYears(1);
                if (!due.isBefore(today) && !due.isAfter(horizon)) {
                    reminders.add(new Reminder(member.getName(), "预备期即将到期，请核查转正材料和程序", due, "重点"));
                }
            }
            if ("积极分子".equals(member.getStatus()) && member.getActivistDate() != null) {
                LocalDate due = member.getActivistDate().plusYears(1);
                if (!due.isBefore(today) && !due.isAfter(horizon)) {
                    reminders.add(new Reminder(member.getName(), "培养考察节点临近，请按现行文件核查发展条件", due, "提醒"));
                }
            }
        }
        return reminders.stream().sorted(Comparator.comparing(Reminder::dueDate)).toList();
    }

    public byte[] generateMinutes(long meetingId) throws IOException {
        Meeting meeting = repository.findMeeting(meetingId);
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addTitle(document, meeting.getTitle() + "会议纪要");
            addLine(document, "会议时间：" + value(meeting.getMeetingDate()));
            addLine(document, "会议地点：" + value(meeting.getLocation()));
            addLine(document, "主持人：" + value(meeting.getHost()));
            addLine(document, "记录人：" + value(meeting.getRecorder()));
            addHeading(document, "一、会议议题");
            addLine(document, value(meeting.getAgenda()));
            addHeading(document, "二、讨论情况");
            addLine(document, value(meeting.getDiscussion()));
            addHeading(document, "三、形成决议");
            addLine(document, value(meeting.getDecisions()));
            addHeading(document, "四、后续任务");
            addLine(document, "请根据会议决议补充责任人和完成期限。");
            document.write(output);
            return output.toByteArray();
        }
    }

    private static void addTitle(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        paragraph.createRun().setText(text);
    }

    private static void addHeading(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setBold(true);
        paragraph.createRun().setText(text);
    }

    private static void addLine(XWPFDocument document, String text) {
        document.createParagraph().createRun().setText(text);
    }

    private static String value(Object value) {
        return value == null || value.toString().isBlank() ? "（未填写）" : value.toString();
    }
}
