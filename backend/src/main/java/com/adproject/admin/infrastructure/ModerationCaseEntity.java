package com.adproject.admin.infrastructure;

import com.adproject.admin.domain.ModerationSourceType;
import com.adproject.admin.domain.ModerationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "moderation_cases")
public class ModerationCaseEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private ModerationSourceType sourceType;
    @Column(name = "source_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String sourceId;
    @Column(name = "author_id", length = 36, columnDefinition = "char(36)")
    private String authorId;
    @Column(name = "content_snapshot", nullable = false, columnDefinition = "TEXT")
    private String contentSnapshot;
    @Column(name = "report_reason", nullable = false, length = 500)
    private String reportReason;
    @Column(name = "report_count", nullable = false)
    private int reportCount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ModerationStatus status;
    @Column(nullable = false)
    private int version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ModerationCaseEntity() {}

    public ModerationCaseEntity(String id, ModerationSourceType sourceType, String sourceId, String authorId,
                                String contentSnapshot, String reportReason, int reportCount, Instant now) {
        this.id = id;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.authorId = authorId;
        this.contentSnapshot = contentSnapshot;
        this.reportReason = reportReason;
        this.reportCount = reportCount;
        this.status = ModerationStatus.PENDING;
        this.version = 1;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() { return id; }
    public ModerationSourceType getSourceType() { return sourceType; }
    public String getSourceId() { return sourceId; }
    public String getAuthorId() { return authorId; }
    public String getContentSnapshot() { return contentSnapshot; }
    public String getReportReason() { return reportReason; }
    public int getReportCount() { return reportCount; }
    public ModerationStatus getStatus() { return status; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void decide(ModerationStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
        this.version++;
    }

    public void addReport(Instant now) {
        this.reportCount++;
        this.updatedAt = now;
        this.version++;
    }
}
