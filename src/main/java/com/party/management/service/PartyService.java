package com.party.management.service;

import com.party.management.model.Activity;
import com.party.management.model.ActivityMinutes;
import com.party.management.model.ActivityFile;
import com.party.management.model.Meeting;
import com.party.management.model.Member;
import com.party.management.model.Reminder;
import com.party.management.repository.PartyRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.Set;

@Service
public class PartyService {
    private final PartyRepository repository;

    public PartyService(PartyRepository repository) {
        this.repository = repository;
    }

    public long countMembers() { return repository.countMembers(); }
    public List<Member> members() { return repository.findMembers(); }
    public List<Member> members(String keyword, String status) { return repository.findMembers(keyword, status); }
    public long countMembers(String keyword, String status) { return repository.countMembers(keyword, status); }
    public List<Member> members(String keyword, String status, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, pageSize);
        return repository.findMembers(keyword, status, (safePage - 1) * safePageSize, safePageSize);
    }
    public Member member(long id) { return repository.findMember(id); }
    public void addMember(Member member) { repository.saveMember(member); }
    public boolean updateMember(Member member) { return repository.updateMember(member); }
    public boolean deleteMember(long id) { return repository.deleteMember(id); }
    public List<Activity> activities() { return repository.findActivities(); }
    public List<Activity> activities(String startDate, String endDate, String type) {
        return repository.findActivities(startDate, endDate, type);
    }
    public Activity activity(long id) { return repository.findActivity(id); }
    public List<Member> excusedMembers(Activity activity, List<Member> participants, List<Member> memberList) {
        Set<Long> participantIds = participants.stream().map(Member::getId).collect(java.util.stream.Collectors.toSet());
        return memberList.stream()
                .filter(member -> "支部委员会".equals(activity.getType())
                        ? member.isCommitteeMember()
                        : "正式党员".equals(member.getStatus()) || "预备党员".equals(member.getStatus()))
                .filter(member -> !participantIds.contains(member.getId()))
                .toList();
    }
    public ActivityMinutes activityMinutes(long activityId) { return repository.findActivityMinutes(activityId); }
    public void saveActivityMinutes(ActivityMinutes minutes) { repository.saveActivityMinutes(minutes); }
    public List<Member> activityParticipants(long activityId) { return repository.findActivityParticipants(activityId); }
    public List<ActivityFile> activityImages(long activityId) { return repository.findActivityImages(activityId); }
    public long addActivity(Activity activity) { return repository.saveActivity(activity); }
    public boolean updateActivity(Activity activity) { return repository.updateActivity(activity); }
    @Transactional
    public boolean deleteActivity(long id) {
        boolean deleted = repository.deleteActivity(id);
        if (deleted) {
            Path directory = Path.of(System.getProperty("user.dir"), "data", "activity-files", String.valueOf(id)).toAbsolutePath().normalize();
            try (var paths = Files.walk(directory)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                });
            } catch (IOException | RuntimeException ignored) {
                // Database deletion is still complete if an old attachment is locked.
            }
        }
        return deleted;
    }

    @Transactional
    public void replaceActivityParticipants(long activityId, Collection<Long> memberIds) {
        if (repository.findActivity(activityId) == null) {
            throw new IllegalArgumentException("活动不存在");
        }
        repository.replaceActivityParticipants(activityId, memberIds);
    }

    @Transactional
    public List<ActivityFile> saveActivityImages(long activityId, List<MultipartFile> files) throws IOException {
        if (repository.findActivity(activityId) == null) {
            throw new IllegalArgumentException("活动不存在");
        }
        List<MultipartFile> uploads = files == null ? List.of() : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (uploads.isEmpty()) throw new IllegalArgumentException("请至少选择一张活动图片");
        if (uploads.size() > 8) throw new IllegalArgumentException("一次最多上传 8 张活动图片");
        long currentCount = activityImages(activityId).size();
        if (currentCount + uploads.size() > 12) throw new IllegalArgumentException("每项活动最多保存 12 张图片");

        for (MultipartFile file : uploads) {
            if (imageContentType(file) == null) {
                throw new IllegalArgumentException("活动图片仅支持 JPG、PNG、WEBP 或 HEIC 格式");
            }
            if (file.getSize() > 10 * 1024 * 1024) throw new IllegalArgumentException("单张活动图片不能超过 10MB");
        }

        Path directory = Path.of(System.getProperty("user.dir"), "data", "activity-files", String.valueOf(activityId)).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        List<Path> savedPaths = new ArrayList<>();
        try {
            for (MultipartFile file : uploads) {
            String contentType = imageContentType(file);
            String originalName = file.getOriginalFilename() == null ? "活动图片" : file.getOriginalFilename();
            String safeName = originalName.replaceAll("[^\\p{L}\\p{N}._-]", "_");
            Path destination = directory.resolve(System.currentTimeMillis() + "-" + java.util.UUID.randomUUID() + "-" + safeName).normalize();
            if (!destination.getParent().equals(directory)) throw new IllegalArgumentException("图片文件名无效");
            file.transferTo(destination.toFile());
            savedPaths.add(destination);
            repository.saveActivityFile(activityId, originalName, destination.toString(), contentType);
            }
        } catch (IOException | RuntimeException ex) {
            for (Path savedPath : savedPaths) {
                try { Files.deleteIfExists(savedPath); } catch (IOException ignored) { }
            }
            throw ex;
        }
        return activityImages(activityId);
    }

    private static String imageContentType(MultipartFile file) {
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(contentType)) return "image/jpeg";
        if (Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif").contains(contentType)) {
            return contentType;
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0) return null;
        return switch (name.substring(dot + 1)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "heic" -> "image/heic";
            case "heif" -> "image/heif";
            default -> null;
        };
    }

    public org.springframework.core.io.Resource activityFile(long fileId) {
        PartyRepository.ActivityFileRecord file = repository.findActivityFile(fileId);
        if (file == null || file.fileType() == null || !file.fileType().startsWith("image/")) return null;
        Path path = Path.of(file.storedPath()).toAbsolutePath().normalize();
        Path root = Path.of(System.getProperty("user.dir"), "data", "activity-files").toAbsolutePath().normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) return null;
        return new org.springframework.core.io.FileSystemResource(path);
    }

    public String activityFileContentType(long fileId) {
        PartyRepository.ActivityFileRecord file = repository.findActivityFile(fileId);
        return file == null ? null : file.fileType();
    }

    public boolean deleteActivityFile(long fileId) throws IOException {
        PartyRepository.ActivityFileRecord file = repository.findActivityFile(fileId);
        if (file == null || file.fileType() == null || !file.fileType().startsWith("image/")) {
            return false;
        }
        Path path = Path.of(file.storedPath()).toAbsolutePath().normalize();
        Path root = Path.of(System.getProperty("user.dir"), "data", "activity-files").toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            return false;
        }
        boolean deleted = repository.deleteActivityFile(fileId);
        if (deleted) {
            Files.deleteIfExists(path);
        }
        return deleted;
    }

    public byte[] exportActivitiesCsv(List<Activity> activities) {
        StringBuilder csv = new StringBuilder("日期,活动主题,活动类型,地点,参加人数,已匹配党员\n");
        for (Activity activity : activities) {
            String names = repository.findActivityParticipants(activity.getId()).stream()
                    .map(Member::getName).collect(java.util.stream.Collectors.joining("、"));
            csv.append(csvCell(activity.getActivityDate())).append(',')
                    .append(csvCell(activity.getTitle())).append(',')
                    .append(csvCell(activity.getType())).append(',')
                    .append(csvCell(activity.getLocation())).append(',')
                    .append(csvCell(activity.getParticipantCount())).append(',')
                    .append(csvCell(names)).append('\n');
        }
        return ("\uFEFF" + csv).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String csvCell(Object value) {
        String text = value == null ? "" : value.toString();
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
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

    public byte[] generateActivityMinutesDocument() throws IOException {
        List<Activity> activities = activities().stream()
                .filter(Activity::isMinutesSaved)
                .sorted(Comparator.comparing(Activity::getActivityDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Activity::getId))
                .toList();
        List<Member> memberList = members();
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addLedgerTitle(document, "信息技术部第六党支部台账");
            for (int index = 0; index < activities.size(); index++) {
                Activity activity = activities.get(index);
                ActivityMinutes minutes = activityMinutes(activity.getId());
                List<Member> participants = activityParticipants(activity.getId());
                List<String> excused = excusedMembers(activity, participants, memberList).stream()
                        .map(Member::getName).toList();
                String meetingDate = activity.getActivityDate() == null ? "未填写" : activity.getActivityDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日"));
                String activityHeading = activity.getActivityDate() == null
                        ? "信息技术部第六党支部" + activityTypeLabel(activity.getType())
                        : "信息技术部第六党支部" + chineseMonth(activity.getActivityDate().getMonthValue()) + "月" + activityTypeLabel(activity.getType());
                addHeading(document, chineseOrdinal(index + 1) + "、" + meetingDate + "，" + activityHeading);
                addIndentedLine(document, "主题：" + value(activity.getTitle()));
                addIndentedLine(document, "地点：" + value(activity.getLocation()));
                String participantText = "支部委员会".equals(activity.getType())
                        ? (participants.isEmpty() ? "未登记" : participants.stream().map(Member::getName).collect(java.util.stream.Collectors.joining("、")))
                        : "总行机关信息技术部第六党支部全体党员";
                addIndentedLine(document, "参会人员：" + participantText);
                addIndentedLine(document, "请假人员：" + (excused.isEmpty() ? "无" : String.join("、", excused)));
                if (minutes.getContent() != null && !minutes.getContent().isBlank()) {
                    addPlainHeading(document, "一、第一议题学习：");
                    addIndentedMultiline(document, minutes.getContent(), minutes);
                }
                if (minutes.getDecisions() != null && !minutes.getDecisions().isBlank()) {
                    addPlainHeading(document, minutes.getContent() == null || minutes.getContent().isBlank() ? "一、内容：" : "二、内容：");
                    addIndentedMultiline(document, minutes.getDecisions(), minutes);
                }
                if (index < activities.size() - 1) addRecordGap(document);
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private static String chineseOrdinal(int number) {
        String[] digits = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (number < 10) return digits[number];
        if (number < 20) return "十" + (number == 10 ? "" : digits[number % 10]);
        if (number < 100) return digits[number / 10] + "十" + (number % 10 == 0 ? "" : digits[number % 10]);
        return Integer.toString(number);
    }

    private static String activityTypeLabel(String type) {
        if (type == null || type.isBlank()) return "组织生活活动";
        if ("主题党日".equals(type)) return "党日活动";
        return type;
    }

    private static String chineseMonth(int month) {
        String[] digits = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二"};
        return month >= 1 && month <= 12 ? digits[month] : Integer.toString(month);
    }

    private static void addMultiline(XWPFDocument document, String text) {
        if (text == null || text.isBlank()) {
            addLine(document, "（未填写）");
            return;
        }
        for (String line : text.split("\\R", -1)) addLine(document, line);
    }

    private static void addIndentedMultiline(XWPFDocument document, String text, ActivityMinutes minutes) {
        if (text == null || text.isBlank()) {
            addIndentedLine(document, "（未填写）");
            return;
        }
        for (String line : text.split("\\R", -1)) {
            if (minutes.isNumberedParagraph(line)) addLine(document, line);
            else addIndentedLine(document, line);
        }
    }

    private static void addTitle(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun run = paragraph.createRun();
        styleRun(run);
        run.setText(text);
    }

    private static void addLedgerTitle(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(480);
        org.apache.poi.xwpf.usermodel.XWPFRun run = paragraph.createRun();
        styleRun(run, 20);
        run.setBold(true);
        run.setText(text);
    }

    private static void addRecordGap(XWPFDocument document) {
        for (int line = 0; line < 2; line++) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setSpacingBefore(0);
            paragraph.setSpacingAfter(0);
            org.apache.poi.xwpf.usermodel.XWPFRun run = paragraph.createRun();
            styleRun(run);
            run.setText(" ");
        }
    }

    private static void addHeading(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun run = paragraph.createRun();
        styleRun(run);
        run.setBold(true);
        run.setText(text);
    }

    private static void addPlainHeading(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun run = paragraph.createRun();
        styleRun(run);
        run.setText(text);
    }

    private static void addLine(XWPFDocument document, String text) {
        org.apache.poi.xwpf.usermodel.XWPFRun run = document.createParagraph().createRun();
        styleRun(run);
        run.setText(text);
    }

    private static void addIndentedLine(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setFirstLineIndent(480);
        org.apache.poi.xwpf.usermodel.XWPFRun run = paragraph.createRun();
        styleRun(run);
        run.setText(text);
    }

    private static void styleRun(org.apache.poi.xwpf.usermodel.XWPFRun run) {
        styleRun(run, 12);
    }

    private static void styleRun(org.apache.poi.xwpf.usermodel.XWPFRun run, int fontSize) {
        run.setFontSize(fontSize);
        run.setFontFamily("宋体");
        run.setFontFamily("宋体", org.apache.poi.xwpf.usermodel.XWPFRun.FontCharRange.eastAsia);
        run.setFontFamily("宋体", org.apache.poi.xwpf.usermodel.XWPFRun.FontCharRange.ascii);
        run.setFontFamily("宋体", org.apache.poi.xwpf.usermodel.XWPFRun.FontCharRange.hAnsi);
    }

    private static String value(Object value) {
        return value == null || value.toString().isBlank() ? "（未填写）" : value.toString();
    }
}
