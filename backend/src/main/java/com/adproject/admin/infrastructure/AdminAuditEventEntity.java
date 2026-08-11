package com.adproject.admin.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "admin_audit_events")
public class AdminAuditEventEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;
    @Column(name = "actor_id", length = 36, columnDefinition = "char(36)")
    private String actorId;
    @Column(nullable = false, length = 64)
    private String action;
    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;
    @Column(name = "target_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String targetId;
    @Column(name = "before_state", columnDefinition = "TEXT")
    private String beforeState;
    @Column(name = "after_state", columnDefinition = "TEXT")
    private String afterState;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AdminAuditEventEntity() {}

    public AdminAuditEventEntity(String id, String actorId, String action, String targetType, String targetId,
                                 String beforeState, String afterState, String reason, String requestId,
                                 Instant occurredAt) {
        this.id = id;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.reason = reason;
        this.requestId = requestId;
        this.occurredAt = occurredAt;
    }

    public String getId() { return id; }
    public String getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getBeforeState() { return beforeState; }
    public String getAfterState() { return afterState; }
    public String getReason() { return reason; }
    public String getRequestId() { return requestId; }
    public Instant getOccurredAt() { return occurredAt; }
}
