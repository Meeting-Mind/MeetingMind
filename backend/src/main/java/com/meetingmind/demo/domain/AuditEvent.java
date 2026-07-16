package com.meetingmind.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
public class AuditEvent {
    @Id String id;
    @Column(name = "space_id", nullable = false) String spaceId;
    @Column(name = "actor_user_id") String actorUserId;
    @Column(nullable = false) String action;
    @Column(name = "target_type", nullable = false) String targetType;
    @Column(name = "target_id", nullable = false) String targetId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "before_value", columnDefinition = "jsonb") Map<String, String> beforeValue;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "after_value", columnDefinition = "jsonb") Map<String, String> afterValue;
    @Column(name = "occurred_at", nullable = false) Instant createdAt;

    protected AuditEvent() { }
    public AuditEvent(String id, String type, String actorUserId, String targetUserId, String resourceId,
                      String beforeValue, String afterValue, Instant createdAt) {
        this.id=id; this.actorUserId=actorUserId; this.action=type; this.targetId=targetUserId == null || targetUserId.isBlank() ? resourceId : targetUserId;
        this.beforeValue=value(resourceId,beforeValue); this.afterValue=value(resourceId,afterValue); this.createdAt=createdAt;
    }
    public String id(){return id;} public String type(){return action;} public String actorUserId(){return actorUserId;}
    public String targetUserId(){return targetId;} public String resourceId(){return valueOf(afterValue,beforeValue,"resourceId");}
    public String beforeValue(){return valueOf(beforeValue,null,"value");} public String afterValue(){return valueOf(afterValue,null,"value");}
    public Instant createdAt(){return createdAt;}
    private static Map<String,String> value(String resourceId,String value){return value == null ? null : Map.of("resourceId",resourceId,"value",value);}
    private static String valueOf(Map<String,String> first,Map<String,String> second,String key){return first != null ? first.get(key) : second == null ? null : second.get(key);}
    @Override public boolean equals(Object other){return other instanceof AuditEvent value && Objects.equals(id,value.id);}
    @Override public int hashCode(){return Objects.hashCode(id);}
}
