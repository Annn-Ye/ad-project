package com.adproject.admin.api;

import com.adproject.admin.api.AdminDtos.AdminAccessRequest;
import com.adproject.admin.api.AdminDtos.AdminUserListResponse;
import com.adproject.admin.api.AdminDtos.AdminUserResponse;
import com.adproject.admin.api.AdminDtos.AuditEventListResponse;
import com.adproject.admin.api.AdminDtos.CompanyReviewListResponse;
import com.adproject.admin.api.AdminDtos.CompanyReviewResponse;
import com.adproject.admin.api.AdminDtos.ModerationCaseListResponse;
import com.adproject.admin.api.AdminDtos.ModerationCaseResponse;
import com.adproject.admin.api.AdminDtos.ModerationDecisionRequest;
import com.adproject.admin.api.AdminDtos.ReviewDecisionRequest;
import com.adproject.admin.api.AdminDtos.UserStatusRequest;
import com.adproject.admin.application.AdminService;
import com.adproject.admin.domain.ModerationSourceType;
import com.adproject.admin.domain.ModerationStatus;
import com.adproject.common.api.RequestIdFilter;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.company.domain.CompanyVerificationStatus;
import com.adproject.user.domain.UserRole;
import com.adproject.user.domain.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/me")
    AdminUserResponse me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return adminService.me(currentUser);
    }

    @GetMapping("/users")
    AdminUserListResponse users(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                @RequestParam(required = false) String q,
                                @RequestParam(required = false) UserRole role,
                                @RequestParam(required = false) UserStatus status,
                                @RequestParam(required = false) Boolean adminAccess,
                                @RequestParam(defaultValue = "1") @Min(1) int page,
                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return adminService.listUsers(currentUser, q, role, status, adminAccess, page, pageSize);
    }

    @GetMapping("/users/{userId}")
    AdminUserResponse user(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable String userId) {
        return adminService.getUser(currentUser, userId);
    }

    @PostMapping("/users/{userId}/status")
    AdminUserResponse userStatus(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                 @PathVariable String userId, @Valid @RequestBody UserStatusRequest request,
                                 HttpServletRequest servletRequest) {
        return adminService.changeUserStatus(currentUser, userId, request,
                RequestIdFilter.current(servletRequest));
    }

    @PostMapping("/users/{userId}/admin-access")
    AdminUserResponse adminAccess(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                  @PathVariable String userId, @Valid @RequestBody AdminAccessRequest request,
                                  HttpServletRequest servletRequest) {
        return adminService.changeAdminAccess(currentUser, userId, request,
                RequestIdFilter.current(servletRequest));
    }

    @GetMapping("/company-reviews")
    CompanyReviewListResponse companyReviews(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(required = false) CompanyVerificationStatus status,
                                             @RequestParam(defaultValue = "1") @Min(1) int page,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return adminService.listCompanies(currentUser, q, status, page, pageSize);
    }

    @GetMapping("/company-reviews/{companyId}")
    CompanyReviewResponse companyReview(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                        @PathVariable String companyId) {
        return adminService.getCompany(currentUser, companyId);
    }

    @PostMapping("/companies/{companyId}/approve")
    CompanyReviewResponse approveCompany(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                          @PathVariable String companyId,
                                          @Valid @RequestBody ReviewDecisionRequest request,
                                          HttpServletRequest servletRequest) {
        return adminService.reviewCompany(currentUser, companyId, request, CompanyVerificationStatus.APPROVED,
                RequestIdFilter.current(servletRequest));
    }

    @PostMapping("/companies/{companyId}/reject")
    CompanyReviewResponse rejectCompany(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                         @PathVariable String companyId,
                                         @Valid @RequestBody ReviewDecisionRequest request,
                                         HttpServletRequest servletRequest) {
        return adminService.reviewCompany(currentUser, companyId, request, CompanyVerificationStatus.REJECTED,
                RequestIdFilter.current(servletRequest));
    }

    @PostMapping("/companies/{companyId}/request-changes")
    CompanyReviewResponse requestCompanyChanges(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                @PathVariable String companyId,
                                                @Valid @RequestBody ReviewDecisionRequest request,
                                                HttpServletRequest servletRequest) {
        return adminService.reviewCompany(currentUser, companyId, request,
                CompanyVerificationStatus.CHANGES_REQUESTED, RequestIdFilter.current(servletRequest));
    }

    @GetMapping("/moderation/cases")
    ModerationCaseListResponse moderationCases(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(required = false) ModerationSourceType sourceType,
                                               @RequestParam(required = false) ModerationStatus status,
                                               @RequestParam(defaultValue = "1") @Min(1) int page,
                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return adminService.listModerationCases(currentUser, q, sourceType, status, page, pageSize);
    }

    @GetMapping("/moderation/cases/{caseId}")
    ModerationCaseResponse moderationCase(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                          @PathVariable String caseId) {
        return adminService.getModerationCase(currentUser, caseId);
    }

    @PostMapping("/moderation/cases/{caseId}/decision")
    ModerationCaseResponse decideModerationCase(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                @PathVariable String caseId,
                                                @Valid @RequestBody ModerationDecisionRequest request,
                                                HttpServletRequest servletRequest) {
        return adminService.decideModerationCase(currentUser, caseId, request,
                RequestIdFilter.current(servletRequest));
    }

    @GetMapping("/audit-events")
    AuditEventListResponse auditEvents(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                       @RequestParam(required = false) String actorId,
                                       @RequestParam(required = false) String action,
                                       @RequestParam(required = false) String targetType,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                       Instant from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                       Instant to,
                                       @RequestParam(defaultValue = "1") @Min(1) int page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return adminService.listAuditEvents(currentUser, actorId, action, targetType, from, to, page, pageSize);
    }
}
