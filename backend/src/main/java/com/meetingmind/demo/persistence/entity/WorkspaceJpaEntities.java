package com.meetingmind.demo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistence-only mappings for non-auth tables. Application services keep using immutable
 * domain records until the JPA WorkspaceStore adapter replaces the JDBC adapter.
 */
final class WorkspaceJpaEntities {
    private WorkspaceJpaEntities() {
    }
}

@Entity
@Table(name = "spaces")
class SpaceEntity {
    @Id String id;
    @Column(nullable = false) String name;
    String description;
    @Column(name = "created_by", nullable = false) String createdBy;
    @Column(name = "deleted_at") Instant deletedAt;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    protected SpaceEntity() { }
}

@Entity
@Table(name = "space_members")
class SpaceMemberEntity {
    @Id String id;
    @Column(name = "space_id", nullable = false) String spaceId;
    @Column(name = "user_id", nullable = false) String userId;
    @Column(nullable = false) String role;
    @Column(name = "joined_at", nullable = false) Instant joinedAt;
    @Column(name = "removed_at") Instant removedAt;
    protected SpaceMemberEntity() { }
}

@Entity
@Table(name = "meetings")
class MeetingEntity {
    @Id String id;
    @Column(name = "space_id", nullable = false) String spaceId;
    @Column(nullable = false) String title;
    @Column(name = "scheduled_at") OffsetDateTime scheduledAt;
    @Column(name = "started_at") OffsetDateTime startedAt;
    @Column(name = "ended_at") OffsetDateTime endedAt;
    @Column(nullable = false) String status;
    @Column(name = "failure_reason") String failureReason;
    @Column(name = "retention_policy", nullable = false) String retentionPolicy;
    @Column(name = "join_code_hash") String joinCodeHash;
    @Column(name = "deleted_at") Instant deletedAt;
    @Column(name = "deleted_by") String deletedBy;
    protected MeetingEntity() { }
}

@Entity
@Table(name = "meeting_participants")
class MeetingParticipantEntity {
    @Id String id;
    @Column(name = "meeting_id", nullable = false) String meetingId;
    @Column(name = "user_id", nullable = false) String userId;
    @Column(nullable = false) String role;
    @Column(name = "participant_type", nullable = false) String participantType;
    @Column(name = "access_status", nullable = false) String accessStatus;
    protected MeetingParticipantEntity() { }
}

@Entity
@Table(name = "meeting_join_requests")
class MeetingJoinRequestEntity {
    @Id String id;
    @Column(name = "meeting_id", nullable = false) String meetingId;
    @Column(name = "user_id", nullable = false) String userId;
    @Column(nullable = false) String status;
    @Column(name = "requested_at", nullable = false) Instant requestedAt;
    @Column(name = "reviewed_at") Instant reviewedAt;
    @Column(name = "reviewed_by") String reviewedBy;
    protected MeetingJoinRequestEntity() { }
}

@Entity
@Table(name = "meeting_speakers")
class MeetingSpeakerEntity {
    @Id String id;
    @Column(name = "meeting_id", nullable = false) String meetingId;
    @Column(nullable = false) String label;
    @Column(name = "display_name") String displayName;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    protected MeetingSpeakerEntity() { }
}

@Entity
@Table(name = "meeting_transcripts")
class MeetingTranscriptEntity {
    @Id @Column(name = "meeting_id") String meetingId;
    @Column(nullable = false) String status;
    String provider;
    String language;
    @Column(name = "started_at") Instant startedAt;
    @Column(name = "completed_at") Instant completedAt;
    @Column(name = "failure_reason") String failureReason;
    @Column(name = "retention_until") Instant retentionUntil;
    @Column(name = "legal_hold", nullable = false) boolean legalHold;
    @Column(name = "purged_at") Instant purgedAt;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    protected MeetingTranscriptEntity() { }
}

@Entity
@Table(name = "transcript_segments")
class TranscriptSegmentEntity {
    @Id String id;
    @Column(name = "meeting_id", nullable = false) String meetingId;
    @Column(name = "speaker_id", nullable = false) String speakerId;
    @Column(name = "speaker_label", nullable = false) String speakerLabel;
    @Column(name = "speaker_name") String speakerName;
    @Column(nullable = false) int sequence;
    @Column(name = "start_ms", nullable = false) int startMs;
    @Column(name = "end_ms", nullable = false) int endMs;
    @Column(nullable = false) String text;
    @Column(nullable = false) String source;
    protected TranscriptSegmentEntity() { }
}

@Entity
@Table(name = "meeting_reports")
class MeetingReportEntity {
    @Id String id;
    @Column(name = "meeting_id", nullable = false) String meetingId;
    @Column(nullable = false) String status;
    @Column(nullable = false) String title;
    @Column(nullable = false) String summary;
    String markdown;
    @Column(name = "created_by") String createdBy;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "source_ids", columnDefinition = "jsonb", nullable = false) List<String> sourceIds;
    @Column(nullable = false) int version;
    @Column(name = "is_current", nullable = false) boolean current;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "confirmed_at") Instant confirmedAt;
    protected MeetingReportEntity() { }
}

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

@Entity
@Table(name = "task_candidates")
class TaskCandidateEntity {
    @Id String id;
    @Column(name = "meeting_id", nullable = false) String meetingId;
    @Column(nullable = false) String title;
    @Column(name = "assignee_name") String assigneeName;
    @Column(name = "suggested_assignee_id") String suggestedAssigneeId;
    @Column(name = "due_date") LocalDate dueDate;
    @Column(nullable = false) String status;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "source_ids", columnDefinition = "jsonb", nullable = false) List<String> sourceIds;
    @Column(name = "created_by", nullable = false) String createdBy;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "confirmed_at") Instant confirmedAt;
    protected TaskCandidateEntity() { }
}

@Entity
@Table(name = "task_cards")
class TaskCardEntity {
    @Id String id;
    @Column(name = "space_id", nullable = false) String spaceId;
    @Column(name = "meeting_id") String meetingId;
    @Column(name = "source_candidate_id") String sourceCandidateId;
    @Column(nullable = false) String title;
    String description;
    @Column(nullable = false) String status;
    @Column(name = "assignee_id") String assigneeId;
    @Column(name = "due_date") LocalDate dueDate;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    protected TaskCardEntity() { }
}

@Entity
@Table(name = "project_knowledge")
class ProjectKnowledgeEntity {
    @Id String id;
    @Column(name = "space_id", nullable = false) String spaceId;
    @Column(nullable = false) String type;
    @Column(nullable = false) String title;
    @Column(nullable = false) String content;
    @Column(name = "source_meeting_id") String sourceMeetingId;
    @Column(name = "approved_by") String approvedBy;
    @Column(nullable = false) String status;
    @Column(name = "embedding_status", nullable = false) String embeddingStatus;
    @Column(name = "embedding_job_id") String embeddingJobId;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "deleted_at") Instant deletedAt;
    protected ProjectKnowledgeEntity() { }
}

@Entity
@Table(name = "domain_terms")
class DomainTermEntity {
    @Id String id;
    @Column(name = "space_id", nullable = false) String spaceId;
    @Column(nullable = false) String term;
    @Column(nullable = false) String definition;
    @Column(nullable = false) String status;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "archived_at") Instant archivedAt;
    protected DomainTermEntity() { }
}

@Entity
@Table(name = "audit_logs")
class AuditLogEntity {
    @Id String id;
    @Column(name = "space_id", nullable = false) String spaceId;
    @Column(name = "actor_user_id") String actorUserId;
    @Column(nullable = false) String action;
    @Column(name = "target_type", nullable = false) String targetType;
    @Column(name = "target_id", nullable = false) String targetId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "before_value", columnDefinition = "jsonb") Map<String, String> beforeValue;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "after_value", columnDefinition = "jsonb") Map<String, String> afterValue;
    @Column(name = "occurred_at", nullable = false) Instant occurredAt;
    protected AuditLogEntity() { }
}

@Entity
@Table(name = "embedding_jobs")
class EmbeddingJobEntity {
    @Id String id;
    @Column(name = "space_id", nullable = false) String spaceId;
    @Column(name = "project_knowledge_id") String projectKnowledgeId;
    @Column(name = "meeting_id") String meetingId;
    @Column(nullable = false) String status;
    String model;
    Integer dimension;
    @Column(nullable = false) int generation;
    @Column(name = "attempt_count", nullable = false) int attemptCount;
    @Column(name = "trigger_reason", nullable = false) String triggerReason;
    @Column(name = "content_hash") String contentHash;
    @Column(name = "next_attempt_at", nullable = false) Instant nextAttemptAt;
    @Column(name = "lease_expires_at") Instant leaseExpiresAt;
    @Column(name = "failure_code") String failureCode;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "started_at") Instant startedAt;
    @Column(name = "completed_at") Instant completedAt;
    protected EmbeddingJobEntity() { }
}

@Entity
@Table(name = "embedding_chunks")
class EmbeddingChunkEntity {
    @Id String id;
    @Column(name = "space_id", nullable = false) String spaceId;
    @Column(name = "project_id", nullable = false) String projectId;
    @Column(name = "meeting_id") String meetingId;
    @Column(name = "project_knowledge_id") String projectKnowledgeId;
    @Column(nullable = false) String scope;
    @Column(name = "source_type", nullable = false) String sourceType;
    @Column(name = "source_id", nullable = false) String sourceId;
    @Column(nullable = false) String title;
    @Column(name = "start_ms") Integer startMs;
    @Column(name = "end_ms") Integer endMs;
    @Column(nullable = false) String content;
    @Column(name = "embedding_text", nullable = false) String embeddingText;
    @Column(name = "embedding_job_id") String embeddingJobId;
    @Column(nullable = false) int generation;
    @Column(name = "is_active", nullable = false) boolean active;
    @Column(name = "replaced_at") Instant replacedAt;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    // vector(1536) and hybrid retrieval remain native SQL/JDBC by decision D-040.
    protected EmbeddingChunkEntity() { }
}
