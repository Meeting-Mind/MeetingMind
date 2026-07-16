package com.meetingmind.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "meeting_reports")
public class MeetingReport {
    @Id String id;
    @Column(name = "meeting_id", nullable = false) String meetingId;
    @Column(nullable = false) String status;
    @Column(nullable = false) String title;
    @Column(nullable = false) String summary;
    String markdown;
    @Transient List<ReportDecision> decisions = List.of();
    @Transient List<ReportActionItem> actionItems = List.of();
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "source_ids", columnDefinition = "jsonb", nullable = false) List<String> sourceIds;
    @Column(name = "created_by") String createdBy;
    @Column(nullable = false) int version;
    @Column(name = "is_current", nullable = false) boolean current;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "confirmed_at") Instant confirmedAt;

    protected MeetingReport() { }

    public MeetingReport(String id, String meetingId, MeetingReportStatus status, String title, String summary, String markdown,
                         List<ReportDecision> decisions, List<ReportActionItem> actionItems, List<String> sourceIds,
                         String createdBy, int version, boolean current, Instant createdAt, Instant confirmedAt) {
        this.id=id; this.meetingId=meetingId; this.status=status.name(); this.title=title; this.summary=summary; this.markdown=markdown;
        this.decisions=decisions == null ? List.of() : List.copyOf(decisions); this.actionItems=actionItems == null ? List.of() : List.copyOf(actionItems);
        this.sourceIds=sourceIds == null ? List.of() : List.copyOf(sourceIds); this.createdBy=createdBy; this.version=version; this.current=current;
        this.createdAt=createdAt; this.confirmedAt=confirmedAt;
    }

    public String id(){return id;} public String meetingId(){return meetingId;} public MeetingReportStatus status(){return MeetingReportStatus.valueOf(status);}
    public String title(){return title;} public String summary(){return summary;} public String markdown(){return markdown;}
    public List<ReportDecision> decisions(){return List.copyOf(decisions);} public List<ReportActionItem> actionItems(){return List.copyOf(actionItems);}
    public List<String> sourceIds(){return List.copyOf(sourceIds);} public String createdBy(){return createdBy;} public int version(){return version;}
    public boolean current(){return current;} public Instant createdAt(){return createdAt;} public Instant confirmedAt(){return confirmedAt;}
    public MeetingReport confirmed(Instant value){return new MeetingReport(id,meetingId,MeetingReportStatus.CONFIRMED,title,summary,markdown,decisions,actionItems,sourceIds,createdBy,version,true,createdAt,value);}
    public MeetingReport withoutCurrent(){return new MeetingReport(id,meetingId,status(),title,summary,markdown,decisions,actionItems,sourceIds,createdBy,version,false,createdAt,confirmedAt);}
    @Override public boolean equals(Object other){return other instanceof MeetingReport value && Objects.equals(id,value.id);}
    @Override public int hashCode(){return Objects.hashCode(id);}

    public record ReportDecision(String id, String title, String content, List<String> sourceIds) {
        public ReportDecision { sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds); }
    }
    public record ReportActionItem(String id, String title, String assigneeName, String dueDate, List<String> sourceIds) {
        public ReportActionItem { sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds); }
    }
}
