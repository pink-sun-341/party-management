package com.party.management.repository;

import com.party.management.model.Activity;
import com.party.management.model.Meeting;
import com.party.management.model.Member;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

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
        return jdbc.query("SELECT * FROM activities ORDER BY activity_date DESC", (rs, rowNum) ->
                new Activity(rs.getLong("id"), rs.getString("title"), rs.getString("type"),
                        date(rs, "activity_date"), rs.getString("location"), rs.getInt("participant_count")));
    }

    public void saveActivity(Activity activity) {
        jdbc.update("INSERT INTO activities(title, type, activity_date, location, participant_count) VALUES (?, ?, ?, ?, ?)",
                activity.getTitle(), activity.getType(), sqlDate(activity.getActivityDate()),
                activity.getLocation(), activity.getParticipantCount());
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

    private static Date sqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private static LocalDate date(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }
}
