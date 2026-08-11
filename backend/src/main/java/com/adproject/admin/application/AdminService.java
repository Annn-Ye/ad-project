package com.adproject.admin.application;

import com.adproject.admin.api.AdminDtos.AdminAccessRequest;
import com.adproject.admin.api.AdminDtos.AdminUser;
import com.adproject.admin.api.AdminDtos.AdminUserListResponse;
import com.adproject.admin.api.AdminDtos.AdminUserResponse;
import com.adproject.admin.api.AdminDtos.AuditEvent;
import com.adproject.admin.api.AdminDtos.AuditEventListResponse;
import com.adproject.admin.api.AdminDtos.CompanyRef;
import com.adproject.admin.api.AdminDtos.CompanyReview;
import com.adproject.admin.api.AdminDtos.CompanyReviewListResponse;
import com.adproject.admin.api.AdminDtos.CompanyReviewResponse;
import com.adproject.admin.api.AdminDtos.ModerationCase;
import com.adproject.admin.api.AdminDtos.ModerationCaseListResponse;
import com.adproject.admin.api.AdminDtos.ModerationCaseResponse;
import com.adproject.admin.api.AdminDtos.PageMeta;
import com.adproject.admin.api.AdminDtos.ModerationDecisionRequest;
import com.adproject.admin.api.AdminDtos.ReviewDecisionRequest;
import com.adproject.admin.api.AdminDtos.UserStatusRequest;
import com.adproject.admin.domain.ModerationDecision;
import com.adproject.admin.domain.ModerationSourceType;
import com.adproject.admin.domain.ModerationStatus;
import com.adproject.admin.infrastructure.AdminAuditEventEntity;
import com.adproject.admin.infrastructure.AdminAuditEventRepository;
import com.adproject.admin.infrastructure.AdminGrantEntity;
import com.adproject.admin.infrastructure.AdminGrantRepository;
import com.adproject.admin.infrastructure.ModerationCaseEntity;
import com.adproject.admin.infrastructure.ModerationCaseRepository;
import com.adproject.auth.infrastructure.RefreshTokenRepository;
import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.company.domain.CompanyVerificationStatus;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.domain.UserStatus;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final AdminGrantRepository grantRepository;
    private final AdminAuditEventRepository auditRepository;
    private final ModerationCaseRepository moderationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdminService(UserRepository userRepository, CompanyRepository companyRepository,
                        CompanyMemberRepository companyMemberRepository, AdminGrantRepository grantRepository,
                        AdminAuditEventRepository auditRepository, ModerationCaseRepository moderationRepository,
                        RefreshTokenRepository refreshTokenRepository, ObjectMapper objectMapper, Clock clock) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.companyMemberRepository = companyMemberRepository;
        this.grantRepository = grantRepository;
        this.auditRepository = auditRepository;
        this.moderationRepository = moderationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminUserResponse me(AuthenticatedUser currentUser) {
        requireAdmin(currentUser);
        return new AdminUserResponse(toUser(requireUser(currentUser.userId())));
    }

    @Transactional(readOnly = true)
    public AdminUserListResponse listUsers(AuthenticatedUser currentUser, String q, UserRole role,
                                           UserStatus status, Boolean adminAccess, int page, int pageSize) {
        requireAdmin(currentUser);
        Specification<UserEntity> specification = (root, query, builder) -> builder.conjunction();
        if (q != null && !q.isBlank()) {
            String value = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("fullName")), value),
                    builder.like(builder.lower(root.get("email")), value)));
        }
        if (role != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("role"), role));
        }
        if (status != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (adminAccess != null) {
            specification = specification.and((root, query, builder) -> {
                Subquery<String> subquery = query.subquery(String.class);
                var grant = subquery.from(AdminGrantEntity.class);
                subquery.select(grant.get("userId")).where(builder.isTrue(grant.get("active")));
                return adminAccess ? root.get("id").in(subquery) : builder.not(root.get("id").in(subquery));
            });
        }
        var result = userRepository.findAll(specification, PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new AdminUserListResponse(result.getContent().stream().map(this::toUser).toList(),
                new PageMeta(page, pageSize, result.getTotalElements(), result.hasNext()));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(AuthenticatedUser currentUser, String userId) {
        requireAdmin(currentUser);
        return new AdminUserResponse(toUser(requireUser(userId)));
    }

    @Transactional
    public AdminUserResponse changeUserStatus(AuthenticatedUser currentUser, String userId,
                                              UserStatusRequest request, String requestId) {
        requireAdmin(currentUser);
        if (currentUser.userId().equals(userId)) {
            throw conflict("SELF_ADMIN_CHANGE_NOT_ALLOWED", "Administrators cannot change their own account status");
        }
        UserEntity user = userRepository.findByIdForUpdate(userId).orElseThrow(AdminService::userNotFound);
        requireVersion(user.getVersion(), request.expectedVersion(), "user");
        if (user.getStatus() == request.status()) return new AdminUserResponse(toUser(user));
        if (request.status() == UserStatus.DISABLED) {
            grantRepository.lockActiveAdministrators();
            boolean targetIsAdmin = grantRepository.existsByUserIdAndActiveTrue(userId);
            if (targetIsAdmin && grantRepository.countActiveAdministrators() <= 1) {
                throw conflict("LAST_ADMIN_PROTECTED", "The final active administrator cannot be disabled");
            }
        }
        Instant now = clock.instant();
        UserStatus before = user.getStatus();
        user.changeStatus(request.status(), now);
        if (request.status() == UserStatus.DISABLED) {
            refreshTokenRepository.revokeAllActiveForUser(userId, now);
        }
        saveAudit(currentUser.userId(), "USER_STATUS_CHANGED", "USER", userId,
                Map.of("status", before.name()), Map.of("status", request.status().name()),
                request.reason(), requestId, now);
        userRepository.flush();
        return new AdminUserResponse(toUser(user));
    }

    @Transactional
    public AdminUserResponse changeAdminAccess(AuthenticatedUser currentUser, String userId,
                                               AdminAccessRequest request, String requestId) {
        requireAdmin(currentUser);
        if (currentUser.userId().equals(userId) && !request.enabled()) {
            throw conflict("SELF_ADMIN_CHANGE_NOT_ALLOWED", "Administrators cannot revoke their own access");
        }
        UserEntity user = userRepository.findByIdForUpdate(userId).orElseThrow(AdminService::userNotFound);
        requireVersion(user.getVersion(), request.expectedVersion(), "user");
        if (!request.enabled()) {
            // Serialize revocations with admin-account disables so two concurrent requests cannot remove every admin.
            grantRepository.lockActiveAdministrators();
        }
        AdminGrantEntity grant = grantRepository.findByUserIdForUpdate(userId).orElse(null);
        boolean before = grant != null && grant.isActive();
        if (before == request.enabled()) return new AdminUserResponse(toUser(user));
        if (!request.enabled() && user.getStatus() == UserStatus.ACTIVE
                && grantRepository.countActiveAdministrators() <= 1) {
            throw conflict("LAST_ADMIN_PROTECTED", "The final active administrator cannot be revoked");
        }
        Instant now = clock.instant();
        if (grant == null) {
            grant = new AdminGrantEntity(userId, currentUser.userId(), now);
            grantRepository.save(grant);
        } else {
            grant.setActive(request.enabled(), currentUser.userId(), now);
        }
        user.touch(now);
        saveAudit(currentUser.userId(), request.enabled() ? "ADMIN_ACCESS_GRANTED" : "ADMIN_ACCESS_REVOKED",
                "USER", userId, Map.of("adminAccess", before), Map.of("adminAccess", request.enabled()),
                request.reason(), requestId, now);
        userRepository.flush();
        return new AdminUserResponse(toUser(user));
    }

    @Transactional(readOnly = true)
    public CompanyReviewListResponse listCompanies(AuthenticatedUser currentUser, String q,
                                                   CompanyVerificationStatus status, int page, int pageSize) {
        requireAdmin(currentUser);
        Specification<CompanyEntity> specification = (root, query, builder) -> builder.conjunction();
        if (q != null && !q.isBlank()) {
            String value = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.like(
                    builder.lower(root.get("name")), value));
        }
        if (status != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("verificationStatus"), status));
        }
        var result = companyRepository.findAll(specification, PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Order.asc("verificationStatus"), Sort.Order.desc("createdAt"))));
        return new CompanyReviewListResponse(result.getContent().stream().map(this::toCompany).toList(),
                new PageMeta(page, pageSize, result.getTotalElements(), result.hasNext()));
    }

    @Transactional(readOnly = true)
    public CompanyReviewResponse getCompany(AuthenticatedUser currentUser, String companyId) {
        requireAdmin(currentUser);
        return new CompanyReviewResponse(toCompany(companyRepository.findById(companyId)
                .orElseThrow(AdminService::companyNotFound)));
    }

    @Transactional
    public CompanyReviewResponse reviewCompany(AuthenticatedUser currentUser, String companyId,
                                               ReviewDecisionRequest request, CompanyVerificationStatus decision,
                                               String requestId) {
        requireAdmin(currentUser);
        CompanyEntity company = companyRepository.findByIdForUpdate(companyId)
                .orElseThrow(AdminService::companyNotFound);
        requireVersion(company.getVersion(), request.expectedVersion(), "company");
        CompanyVerificationStatus before = company.getVerificationStatus();
        if (before != CompanyVerificationStatus.PENDING && before != CompanyVerificationStatus.CHANGES_REQUESTED) {
            throw conflict("INVALID_COMPANY_REVIEW_TRANSITION", "This company is not awaiting review");
        }
        Instant now = clock.instant();
        company.changeVerificationStatus(decision, now);
        saveAudit(currentUser.userId(), "COMPANY_" + decision.name(), "COMPANY", companyId,
                Map.of("verificationStatus", before.name()), Map.of("verificationStatus", decision.name()),
                request.reason(), requestId, now);
        companyRepository.flush();
        return new CompanyReviewResponse(toCompany(company));
    }

    @Transactional(readOnly = true)
    public ModerationCaseListResponse listModerationCases(AuthenticatedUser currentUser, String q,
                                                         ModerationSourceType sourceType, ModerationStatus status,
                                                         int page, int pageSize) {
        requireAdmin(currentUser);
        Specification<ModerationCaseEntity> specification = (root, query, builder) -> builder.conjunction();
        if (q != null && !q.isBlank()) {
            String value = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("contentSnapshot")), value),
                    builder.like(builder.lower(root.get("reportReason")), value)));
        }
        if (sourceType != null) specification = specification.and((root, query, builder) ->
                builder.equal(root.get("sourceType"), sourceType));
        if (status != null) specification = specification.and((root, query, builder) ->
                builder.equal(root.get("status"), status));
        var result = moderationRepository.findAll(specification, PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Order.asc("status"), Sort.Order.desc("createdAt"))));
        return new ModerationCaseListResponse(result.getContent().stream().map(this::toModeration).toList(),
                new PageMeta(page, pageSize, result.getTotalElements(), result.hasNext()));
    }

    @Transactional(readOnly = true)
    public ModerationCaseResponse getModerationCase(AuthenticatedUser currentUser, String caseId) {
        requireAdmin(currentUser);
        return new ModerationCaseResponse(toModeration(moderationRepository.findById(caseId)
                .orElseThrow(AdminService::moderationNotFound)));
    }

    @Transactional
    public ModerationCaseResponse decideModerationCase(AuthenticatedUser currentUser, String caseId,
                                                       ModerationDecisionRequest request, String requestId) {
        requireAdmin(currentUser);
        ModerationCaseEntity moderationCase = moderationRepository.findByIdForUpdate(caseId)
                .orElseThrow(AdminService::moderationNotFound);
        requireVersion(moderationCase.getVersion(), request.expectedVersion(), "moderation case");
        if (moderationCase.getStatus() != ModerationStatus.PENDING) {
            throw conflict("INVALID_MODERATION_TRANSITION", "This moderation case is already decided");
        }
        ModerationStatus target = request.decision() == ModerationDecision.KEEP
                ? ModerationStatus.KEPT : ModerationStatus.REMOVED;
        Instant now = clock.instant();
        moderationCase.decide(target, now);
        saveAudit(currentUser.userId(), "MODERATION_" + target.name(), "MODERATION_CASE", caseId,
                Map.of("status", ModerationStatus.PENDING.name()), Map.of("status", target.name()),
                request.reason(), requestId, now);
        moderationRepository.flush();
        return new ModerationCaseResponse(toModeration(moderationCase));
    }

    @Transactional(readOnly = true)
    public AuditEventListResponse listAuditEvents(AuthenticatedUser currentUser, String actorId, String action,
                                                  String targetType, Instant from, Instant to,
                                                  int page, int pageSize) {
        requireAdmin(currentUser);
        Specification<AdminAuditEventEntity> specification = (root, query, builder) -> builder.conjunction();
        if (actorId != null && !actorId.isBlank()) specification = specification.and((root, query, builder) ->
                builder.equal(root.get("actorId"), actorId));
        if (action != null && !action.isBlank()) specification = specification.and((root, query, builder) ->
                builder.equal(root.get("action"), action));
        if (targetType != null && !targetType.isBlank()) specification = specification.and((root, query, builder) ->
                builder.equal(root.get("targetType"), targetType));
        if (from != null) specification = specification.and((root, query, builder) ->
                builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
        if (to != null) specification = specification.and((root, query, builder) ->
                builder.lessThanOrEqualTo(root.get("occurredAt"), to));
        var result = auditRepository.findAll(specification, PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
        return new AuditEventListResponse(result.getContent().stream().map(this::toAudit).toList(),
                new PageMeta(page, pageSize, result.getTotalElements(), result.hasNext()));
    }

    private AdminUser toUser(UserEntity user) {
        CompanyRef company = companyMemberRepository.findByUserId(user.getId())
                .flatMap(member -> companyRepository.findById(member.getCompanyId()))
                .map(value -> new CompanyRef(value.getId(), value.getName(), value.getVerificationStatus()))
                .orElse(null);
        return new AdminUser(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.getStatus(),
                grantRepository.existsByUserIdAndActiveTrue(user.getId()), company, user.getVersion(),
                user.getCreatedAt(), user.getUpdatedAt());
    }

    private CompanyReview toCompany(CompanyEntity company) {
        UserEntity creator = userRepository.findById(company.getCreatedBy()).orElse(null);
        return new CompanyReview(company.getId(), company.getName(), company.getLogoUrl(), company.getStage(),
                company.getEmployeeRange(), company.getVerificationStatus(), company.getWebsite(),
                company.getDescription(), company.getLocation(), company.getVersion(), company.getCreatedBy(),
                creator == null ? "Unknown user" : creator.getFullName(), creator == null ? null : creator.getEmail(),
                company.getCreatedAt(), company.getUpdatedAt());
    }

    private ModerationCase toModeration(ModerationCaseEntity moderationCase) {
        UserEntity author = moderationCase.getAuthorId() == null ? null
                : userRepository.findById(moderationCase.getAuthorId()).orElse(null);
        return new ModerationCase(moderationCase.getId(), moderationCase.getSourceType(),
                moderationCase.getSourceId(), moderationCase.getAuthorId(),
                author == null ? null : author.getFullName(), moderationCase.getContentSnapshot(),
                moderationCase.getReportReason(), moderationCase.getReportCount(), moderationCase.getStatus(),
                moderationCase.getVersion(), moderationCase.getCreatedAt(), moderationCase.getUpdatedAt());
    }

    private AuditEvent toAudit(AdminAuditEventEntity event) {
        String actorName = event.getActorId() == null ? "System"
                : userRepository.findById(event.getActorId()).map(UserEntity::getFullName).orElse("Unknown user");
        return new AuditEvent(event.getId(), event.getActorId(), actorName, event.getAction(), event.getTargetType(),
                event.getTargetId(), event.getBeforeState(), event.getAfterState(), event.getReason(),
                event.getRequestId(), event.getOccurredAt());
    }

    private UserEntity requireUser(String userId) {
        return userRepository.findById(userId).orElseThrow(AdminService::userNotFound);
    }

    private static void requireAdmin(AuthenticatedUser currentUser) {
        if (currentUser == null || !currentUser.platformAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
    }

    private static void requireVersion(int actual, int expected, String resource) {
        if (actual != expected) throw conflict("VERSION_CONFLICT",
                "The " + resource + " has changed; reload it before continuing");
    }

    private void saveAudit(String actorId, String action, String targetType, String targetId,
                           Object before, Object after, String reason, String requestId, Instant now) {
        auditRepository.save(new AdminAuditEventEntity(UUID.randomUUID().toString(), actorId, action, targetType,
                targetId, json(before), json(after), reason.trim(), requestId, now));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize audit state", exception);
        }
    }

    private static ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found");
    }

    private static ApiException companyNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Company not found");
    }

    private static ApiException moderationNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Moderation case not found");
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
