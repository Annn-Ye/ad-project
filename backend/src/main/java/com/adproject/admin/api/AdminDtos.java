package com.adproject.admin.api;

import com.adproject.admin.domain.ModerationDecision;
import com.adproject.admin.domain.ModerationSourceType;
import com.adproject.admin.domain.ModerationStatus;
import com.adproject.company.domain.CompanyVerificationStatus;
import com.adproject.user.domain.UserRole;
import com.adproject.user.domain.UserStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {}

    public record PageMeta(int page, int pageSize, long total, boolean hasNext) {}
    public record CompanyRef(String companyId, String name, CompanyVerificationStatus verificationStatus) {}
    public record AdminUser(String userId, String fullName, String email, UserRole role, UserStatus status,
                            boolean adminAccess, CompanyRef company, int version,
                            Instant createdAt, Instant updatedAt) {}
    public record CompanyReview(String companyId, String name, String logoUrl, String stage, String employeeRange,
                                CompanyVerificationStatus verificationStatus, String website, String description,
                                String location, int version, String createdByUserId, String createdByName,
                                String createdByEmail, Instant createdAt, Instant updatedAt) {}
    public record ModerationCase(String caseId, ModerationSourceType sourceType, String sourceId, String authorId,
                                 String authorName, String contentSnapshot, String reportReason, int reportCount,
                                 ModerationStatus status, int version, Instant createdAt, Instant updatedAt) {}
    public record AuditEvent(String auditEventId, String actorId, String actorName, String action,
                             String targetType, String targetId, String beforeState, String afterState,
                             String reason, String requestId, Instant occurredAt) {}

    public record AdminUserResponse(AdminUser data) {}
    public record AdminUserListResponse(List<AdminUser> data, PageMeta meta) {}
    public record CompanyReviewResponse(CompanyReview data) {}
    public record CompanyReviewListResponse(List<CompanyReview> data, PageMeta meta) {}
    public record ModerationCaseResponse(ModerationCase data) {}
    public record ModerationCaseListResponse(List<ModerationCase> data, PageMeta meta) {}
    public record AuditEventListResponse(List<AuditEvent> data, PageMeta meta) {}

    public record UserStatusRequest(@NotNull UserStatus status,
                                    @NotBlank @Size(max = 500) String reason,
                                    @Min(1) int expectedVersion) {}
    public record AdminAccessRequest(@NotNull Boolean enabled,
                                     @NotBlank @Size(max = 500) String reason,
                                     @Min(1) int expectedVersion) {}
    public record ReviewDecisionRequest(@NotBlank @Size(max = 500) String reason,
                                        @Min(1) int expectedVersion) {}
    public record ModerationDecisionRequest(@NotNull ModerationDecision decision,
                                             @NotBlank @Size(max = 500) String reason,
                                             @Min(1) int expectedVersion) {}

    public record PageQuery(@Min(1) int page, @Min(1) @Max(100) int pageSize) {}
}
