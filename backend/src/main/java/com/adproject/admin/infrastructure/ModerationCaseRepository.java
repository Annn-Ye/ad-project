package com.adproject.admin.infrastructure;

import com.adproject.admin.domain.ModerationSourceType;
import com.adproject.admin.domain.ModerationStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModerationCaseRepository extends JpaRepository<ModerationCaseEntity, String>,
        JpaSpecificationExecutor<ModerationCaseEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select moderationCase from ModerationCaseEntity moderationCase where moderationCase.id = :caseId")
    Optional<ModerationCaseEntity> findByIdForUpdate(@Param("caseId") String caseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select moderationCase from ModerationCaseEntity moderationCase " +
            "where moderationCase.sourceType = :sourceType and moderationCase.sourceId = :sourceId")
    Optional<ModerationCaseEntity> findBySourceForUpdate(@Param("sourceType") ModerationSourceType sourceType,
                                                         @Param("sourceId") String sourceId);

    boolean existsBySourceTypeAndSourceIdAndStatus(ModerationSourceType sourceType, String sourceId,
                                                    ModerationStatus status);
}
