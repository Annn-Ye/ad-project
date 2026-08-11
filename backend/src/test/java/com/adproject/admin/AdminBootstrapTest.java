package com.adproject.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adproject.admin.application.AdminBootstrap;
import com.adproject.admin.infrastructure.AdminAuditEventEntity;
import com.adproject.admin.infrastructure.AdminAuditEventRepository;
import com.adproject.admin.infrastructure.AdminGrantEntity;
import com.adproject.admin.infrastructure.AdminGrantRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.domain.UserStatus;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class AdminBootstrapTest {
    @Test
    void reactivatesAnExistingGrantOnlyWhenThereIsNoEffectiveAdministrator() {
        Instant now = Instant.parse("2026-08-11T08:00:00Z");
        UserEntity user = new UserEntity("11111111-1111-1111-1111-111111111111", "admin@example.com", "hash",
                "Initial Admin", UserRole.CANDIDATE, UserStatus.ACTIVE, "2026-08", now.minusSeconds(60),
                now.minusSeconds(60));
        AdminGrantEntity revoked = new AdminGrantEntity(user.getId(), null, now.minusSeconds(50));
        revoked.setActive(false, user.getId(), now.minusSeconds(40));
        AdminGrantRepository grants = mock(AdminGrantRepository.class);
        AdminAuditEventRepository audit = mock(AdminAuditEventRepository.class);
        UserRepository users = mock(UserRepository.class);
        when(grants.countActiveAdministrators()).thenReturn(0L);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(user));
        when(grants.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(revoked));

        new AdminBootstrap(" ADMIN@EXAMPLE.COM ", grants, audit, users,
                Clock.fixed(now, ZoneOffset.UTC)).run(mock(ApplicationArguments.class));

        assertThat(revoked.isActive()).isTrue();
        assertThat(revoked.getVersion()).isEqualTo(3);
        assertThat(user.getVersion()).isEqualTo(2);
        verify(audit).save(any(AdminAuditEventEntity.class));
    }
}
