package com.party.management.model;

public class ActivityMinutes {
    private Long activityId;
    private String content;
    private String decisions;
    private String followups;
    private String updatedAt;

    public ActivityMinutes() {}

    public ActivityMinutes(Long activityId, String content, String decisions, String followups, String updatedAt) {
        this.activityId = activityId;
        this.content = content;
        this.decisions = decisions;
        this.followups = followups;
        this.updatedAt = updatedAt;
    }

    public Long getActivityId() { return activityId; }
    public String getContent() { return content; }
    public String getDecisions() { return decisions; }
    public java.util.List<String> getContentParagraphs() { return paragraphs(content); }
    public java.util.List<String> getDecisionParagraphs() { return paragraphs(decisions); }
    public boolean isNumberedParagraph(String text) {
        return text != null && text.matches("^(?:\\d+(?:[.．]\\d+)*[.．、)）]|[一二三四五六七八九十百]+[、.．]|[（(][一二三四五六七八九十百]+[）)]).*");
    }
    public String getFollowups() { return followups; }
    public String getUpdatedAt() { return updatedAt; }
    public void setActivityId(Long value) { this.activityId = value; }
    public void setContent(String value) { this.content = value; }
    public void setDecisions(String value) { this.decisions = value; }
    public void setFollowups(String value) { this.followups = value; }
    public void setUpdatedAt(String value) { this.updatedAt = value; }

    private static java.util.List<String> paragraphs(String text) {
        if (text == null || text.isEmpty()) return java.util.List.of();
        return java.util.Arrays.asList(text.split("\\R", -1));
    }
}
