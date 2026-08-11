package com.adproject.admin.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEventEntity, String>,
        JpaSpecificationExecutor<AdminAuditEventEntity> {}
