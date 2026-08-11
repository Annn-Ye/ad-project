package com.adproject.admin.application;

import com.adproject.admin.infrastructure.AdminAuditEventEntity;
import com.adproject.admin.infrastructure.AdminAuditEventRepository;
import com.adproject.admin.infrastructure.AdminGrantEntity;
import com.adproject.admin.infrastructure.AdminGrantRepository;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import com.adproject.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final String bootstrapEmail;
    private final AdminGrantRepository grantRepository;
    private final AdminAuditEventRepository auditRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public AdminBootstrap(@Value("${app.admin.bootstrap-email:}") String bootstrapEmail,
                          AdminGrantRepository grantRepository, AdminAuditEventRepository auditRepository,
                          UserRepository userRepository, Clock clock) {
        this.bootstrapEmail = bootstrapEmail;
        this.grantRepository = grantRepository;
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (bootstrapEmail == null || bootstrapEmail.isBlank() || grantRepository.countActiveAdministrators() > 0) {
            return;
        }
        UserEntity user = userRepository.findByEmail(bootstrapEmail.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalStateException(
                        "ADMIN_BOOTSTRAP_EMAIL must identify an existing registered account"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_EMAIL must identify an active account");
        }
        Instant now = clock.instant();
        AdminGrantEntity grant = grantRepository.findByUserIdForUpdate(user.getId()).orElse(null);
        if (grant == null) {
            grantRepository.save(new AdminGrantEntity(user.getId(), null, now));
        } else {
            grant.setActive(true, null, now);
        }
        user.touch(now);
        auditRepository.save(new AdminAuditEventEntity(UUID.randomUUID().toString(), null,
                "ADMIN_ACCESS_BOOTSTRAPPED", "USER", user.getId(),
                "{\"adminAccess\":false}", "{\"adminAccess\":true}",
                "Initial administrator bootstrap", "system_bootstrap", now));
    }
}
