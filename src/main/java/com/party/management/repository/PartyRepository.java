package com.party.management.repository;

import com.party.management.model.Activity;
import com.party.management.model.ActivityFile;
import com.party.management.model.Meeting;
import com.party.management.model.Member;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Collection;

@Repository
public class PartyRepository {
    private final JdbcTemplate jdbc;

    public PartyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countMembers() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM members", Long.class);
    }

    public List<Member> findMembers() {
        return findMembers(null, null);
    }

    public List<Member> findMembers(String keyword, String status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM members WHERE 1 = 1");
        List<Object> args = new java.util.ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (name LIKE ? OR phone LIKE ?)");
            String pattern = "%" + keyword.trim() + "%";
            args.add(pattern);
            args.add(pattern);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY name");
        return jdbc.query(sql.toString(), (rs, rowNum) -> new Member(
                rs.getLong("id"), rs.getString("name"), rs.getString("gender"),
                rs.getString("phone"), rs.getString("status"), date(rs, "application_date"),
                date(rs, "activist_date"), date(rs, "development_object_date"),
                date(rs, "probationary_date"), date(rs, "full_member_date")), args.toArray());
    }

    public Member findMember(long id) {
        List<Member> members = jdbc.query("SELECT * FROM members WHERE id = ?", (rs, rowNum) -> new Member(
                rs.getLong("id"), rs.getString("name"), rs.getString("gender"),
                rs.getString("phone"), rs.getString("status"), date(rs, "application_date"),
                date(rs, "activist_date"), date(rs, "development_object_date"),
                date(rs, "probationary_date"), date(rs, "full_member_date")), id);
        return members.isEmpty() ? null : members.get(0);
    }

    public void saveMember(Member member) {
        jdbc.update("""
                INSERT INTO members(name, gender, phone, status, application_date, activist_date,
                    development_object_date, probationary_date, full_member_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, member.getName(), member.getGender(), member.getPhone(), member.getStatus(),
                sqlDate(member.getApplicationDate()), sqlDate(member.getActivistDate()),
                sqlDate(member.getDevelopmentObjectDate()), sqlDate(member.getProbationaryDate()),
                sqlDate(member.getFullMemberDate()));
    }

    public boolean updateMember(Member member) {
        return jdbc.update("""
                UPDATE members SET name = ?, gender = ?, phone = ?, status = ?, application_date = ?,
                    activist_date = ?, development_object_date = ?, probationary_date = ?, full_member_date = ?
                WHERE id = ?
                """, member.getName(), member.getGender(), member.getPhone(), member.getStatus(),
                sqlDate(member.getApplicationDate()), sqlDate(member.getActivistDate()),
                sqlDate(member.getDevelopmentObjectDate()), sqlDate(member.getProbationaryDate()),
                sqlDate(member.getFullMemberDate()), member.getId()) > 0;
    }

    public boolean deleteMember(long id) {
        return jdbc.update("DELETE FROM members WHERE id = ?", id) > 0;
    }

    public List<Activity> findActivities() {
        return findActivities(null, null, null);
    }

    public List<Activity> findActivities(String startDate, String endDate, String type) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.*, CASE WHEN EXISTS (SELECT 1 FROM activity_participants ap WHERE ap.activity_id = a.id)
                    THEN (SELECT COUNT(*) FROM activity_participants ap2 WHERE ap2.activity_id = a.id)
                    ELSE a.participant_count END AS participant_total
                FROM activities a WHERE 1 = 1
                """);
        List<Object> args = new java.util.ArrayList<>();
        if (startDate != null && !startDate.isBlank()) {
            sql.append(" AND a.activity_date >= ?");
            args.add(startDate);
        }
        if (endDate != null && !endDate.isBlank()) {
            sql.append(" AND a.activity_date <= ?");
            args.add(endDate);
        }
        if (type != null && !type.isBlank()) {
            sql.append(" AND a.type = ?");
            args.add(type);
        }
        sql.append(" ORDER BY a.activity_date DESC, a.id DESC");
        return jdbc.query(sql.toString(), (rs, rowNum) -> activity(rs), args.toArray());
    }

    public Activity findActivity(long id) {
        List<Activity> activities = jdbc.query("""
                SELECT a.*, CASE WHEN EXISTS (SELECT 1 FROM activity_participants ap WHERE ap.activity_id = a.id)
                    THEN (SELECT COUNT(*) FROM activity_participants ap2 WHERE ap2.activity_id = a.id)
                    ELSE a.participant_count END AS participant_total
                FROM activities a WHERE a.id = ?
                """, (rs, rowNum) -> activity(rs), id);
        return activities.isEmpty() ? null : activities.get(0);
    }

    public long saveActivity(Activity activity) {
        jdbc.update("INSERT INTO activities(title, type, activity_date, location, participant_count) VALUES (?, ?, ?, ?, ?)",
                activity.getTitle(), activity.getType(), sqlDate(activity.getActivityDate()),
                activity.getLocation(), activity.getParticipantCount() == null ? 0 : activity.getParticipantCount());
        return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    public boolean updateActivity(Activity activity) {
        return jdbc.update("""
                UPDATE activities SET title = ?, type = ?, activity_date = ?, location = ? WHERE id = ?
                """, activity.getTitle(), activity.getType(), sqlDate(activity.getActivityDate()),
                activity.getLocation(), activity.getId()) > 0;
    }

    public boolean deleteActivity(long id) {
        jdbc.update("DELETE FROM activity_files WHERE activity_id = ?", id);
        jdbc.update("DELETE FROM activity_participants WHERE activity_id = ?", id);
        return jdbc.update("DELETE FROM activities WHERE id = ?", id) > 0;
    }

    public List<Member> findActivityParticipants(long activityId) {
        return jdbc.query("""
                SELECT m.* FROM members m
                INNER JOIN activity_participants ap ON ap.member_id = m.id
                WHERE ap.activity_id = ? ORDER BY m.name
                """, (rs, rowNum) -> new Member(
                rs.getLong("id"), rs.getString("name"), rs.getString("gender"),
                rs.getString("phone"), rs.getString("status"), date(rs, "application_date"),
                date(rs, "activist_date"), date(rs, "development_object_date"),
                date(rs, "probationary_date"), date(rs, "full_member_date")), activityId);
    }

    public void replaceActivityParticipants(long activityId, Collection<Long> memberIds) {
        jdbc.update("DELETE FROM activity_participants WHERE activity_id = ?", activityId);
        if (memberIds != null) {
            for (Long memberId : memberIds) {
                if (memberId != null) {
                    jdbc.update("INSERT OR IGNORE INTO activity_participants(activity_id, member_id) VALUES (?, ?)", activityId, memberId);
                }
            }
        }
        jdbc.update("UPDATE activities SET participant_count = (SELECT COUNT(*) FROM activity_participants WHERE activity_id = ?) WHERE id = ?", activityId, activityId);
    }

    public void saveActivityFile(long activityId, String originalName, String storedPath, String fileType) {
        jdbc.update("INSERT INTO activity_files(activity_id, original_name, stored_path, file_type) VALUES (?, ?, ?, ?)",
                activityId, originalName, storedPath, fileType);
    }

    public List<ActivityFile> findActivityImages(long activityId) {
        return jdbc.query("""
                SELECT id, original_name, file_type FROM activity_files
                WHERE activity_id = ? AND file_type LIKE 'image/%' ORDER BY id
                """, (rs, rowNum) -> new ActivityFile(rs.getLong("id"),
                rs.getString("original_name"), rs.getString("file_type"), "/activities/files/" + rs.getLong("id")), activityId);
    }

    public ActivityFileRecord findActivityFile(long fileId) {
        List<ActivityFileRecord> files = jdbc.query("""
                SELECT id, activity_id, original_name, stored_path, file_type
                FROM activity_files WHERE id = ?
                """, (rs, rowNum) -> new ActivityFileRecord(rs.getLong("id"), rs.getLong("activity_id"),
                rs.getString("original_name"), rs.getString("stored_path"), rs.getString("file_type")), fileId);
        return files.isEmpty() ? null : files.get(0);
    }

    public record ActivityFileRecord(Long id, Long activityId, String originalName, String storedPath, String fileType) {}

    private static Activity activity(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Activity(rs.getLong("id"), rs.getString("title"), rs.getString("type"),
                date(rs, "activity_date"), rs.getString("location"), rs.getInt("participant_total"));
    }

    public List<Meeting> findMeetings() {
        return jdbc.query("SELECT * FROM meetings ORDER BY meeting_date DESC", (rs, rowNum) ->
                new Meeting(rs.getLong("id"), rs.getString("title"), date(rs, "meeting_date"),
                        rs.getString("location"), rs.getString("host"), rs.getString("recorder"),
                        rs.getString("agenda"), rs.getString("discussion"), rs.getString("decisions")));
    }

    public Meeting findMeeting(long id) {
        return jdbc.queryForObject("SELECT * FROM meetings WHERE id = ?", (rs, rowNum) ->
                new Meeting(rs.getLong("id"), rs.getString("title"), date(rs, "meeting_date"),
                        rs.getString("location"), rs.getString("host"), rs.getString("recorder"),
                        rs.getString("agenda"), rs.getString("discussion"), rs.getString("decisions")), id);
    }

    public void saveMeeting(Meeting meeting) {
        jdbc.update("""
                INSERT INTO meetings(title, meeting_date, location, host, recorder, agenda, discussion, decisions)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, meeting.getTitle(), sqlDate(meeting.getMeetingDate()), meeting.getLocation(),
                meeting.getHost(), meeting.getRecorder(), meeting.getAgenda(), meeting.getDiscussion(),
                meeting.getDecisions());
    }

    /** Store dates as ISO text so SQLite does not convert java.sql.Date to epoch milliseconds. */
    private static String sqlDate(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private static LocalDate date(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        String value = rs.getString(column);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            // Keep records written by the previous implementation readable.
            try {
                long epochMillis = Long.parseLong(value);
                return Instant.ofEpochMilli(epochMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
            } catch (NumberFormatException ex) {
                throw new java.sql.SQLException("无法解析日期字段 " + column + ": " + value, ex);
            }
        }
    }
}
