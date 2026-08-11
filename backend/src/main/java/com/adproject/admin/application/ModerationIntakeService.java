package com.adproject.admin.application;

import com.adproject.admin.domain.ModerationSourceType;
import com.adproject.admin.domain.ModerationStatus;
import com.adproject.admin.infrastructure.ModerationCaseEntity;
import com.adproject.admin.infrastructure.ModerationCaseRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal boundary used by the future community module; it is intentionally not a public HTTP endpoint. */
@Service
public class ModerationIntakeService {
    private final ModerationCaseRepository repository;
    private final Clock clock;

    public ModerationIntakeService(ModerationCaseRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public CaseReference report(ModerationSourceType sourceType, String sourceId, String authorId,
                                String contentSnapshot, String reportReason) {
        requireText(sourceId, 36, "sourceId");
        requireText(contentSnapshot, 65_535, "contentSnapshot");
        requireText(reportReason, 500, "reportReason");
        Instant now = clock.instant();
        ModerationCaseEntity moderationCase = repository.findBySourceForUpdate(sourceType, sourceId).orElse(null);
        if (moderationCase == null) {
            moderationCase = repository.save(new ModerationCaseEntity(UUID.randomUUID().toString(), sourceType,
                    sourceId, authorId, contentSnapshot.trim(), reportReason.trim(), 1, now));
        } else {
            moderationCase.addReport(now);
        }
        repository.flush();
        return new CaseReference(moderationCase.getId(), moderationCase.getStatus(),
                moderationCase.getReportCount(), moderationCase.getVersion());
    }

    @Transactional(readOnly = true)
    public boolean isRemoved(ModerationSourceType sourceType, String sourceId) {
        return repository.existsBySourceTypeAndSourceIdAndStatus(sourceType, sourceId, ModerationStatus.REMOVED);
    }

    private static void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be non-blank and no longer than " + maxLength);
        }
    }

    public record CaseReference(String caseId, ModerationStatus status, int reportCount, int version) {}
}
