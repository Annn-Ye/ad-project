package com.adproject.company.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyRepository extends JpaRepository<CompanyEntity, String>, JpaSpecificationExecutor<CompanyEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select company from CompanyEntity company where company.id = :companyId")
    Optional<CompanyEntity> findByIdForUpdate(@Param("companyId") String companyId);
}
