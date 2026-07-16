package com.meetingmind.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "report_decisions")
class ReportDecisionEntity {
    @Id String id;
    @Column(name = "report_id", nullable = false) String reportId;
    @Column(name = "decision_order", nullable = false) int decisionOrder;
    @Column(nullable = false) String title;
    String rationale;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "source_ids", columnDefinition = "jsonb", nullable = false) List<String> sourceIds;
    protected ReportDecisionEntity() { }
}

@Entity
@Table(name = "report_action_items")
class ReportActionItemEntity {
    @Id String id;
    @Column(name = "report_id", nullable = false) String reportId;
    @Column(name = "item_order", nullable = false) int itemOrder;
    @Column(nullable = false) String title;
    @Column(name = "assignee_name") String assigneeName;
    @Column(name = "due_date") LocalDate dueDate;
    @Column(name = "confirmation_state", nullable = false) String confirmationState;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "source_ids", columnDefinition = "jsonb", nullable = false) List<String> sourceIds;
    protected ReportActionItemEntity() { }
}
