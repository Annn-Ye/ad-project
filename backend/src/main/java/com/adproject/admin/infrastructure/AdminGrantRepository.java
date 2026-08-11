package com.adproject.admin.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminGrantRepository extends JpaRepository<AdminGrantEntity, String> {
    boolean existsByUserIdAndActiveTrue(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select grant from AdminGrantEntity grant where grant.userId = :userId")
    Optional<AdminGrantEntity> findByUserIdForUpdate(@Param("userId") String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select grant from AdminGrantEntity grant where grant.active = true order by grant.userId")
    List<AdminGrantEntity> lockActiveAdministrators();

    @Query(value = "select count(*) from admin_grants grant_record join users user_record " +
            "on user_record.id = grant_record.user_id where grant_record.active = true and user_record.status = 'ACTIVE'",
            nativeQuery = true)
    long countActiveAdministrators();
}
