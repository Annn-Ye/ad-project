package com.adproject.admin.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "admin_grants")
public class AdminGrantEntity {
    @Id
    @Column(name = "user_id", length = 36, columnDefinition = "char(36)")
    private String userId;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private int version;
    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;
    @Column(name = "granted_by", length = 36, columnDefinition = "char(36)")
    private String grantedBy;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "revoked_by", length = 36, columnDefinition = "char(36)")
    private String revokedBy;

    protected AdminGrantEntity() {}

    public AdminGrantEntity(String userId, String grantedBy, Instant now) {
        this.userId = userId;
        this.active = true;
        this.version = 1;
        this.grantedAt = now;
        this.grantedBy = grantedBy;
    }

    public String getUserId() { return userId; }
    public boolean isActive() { return active; }
    public int getVersion() { return version; }

    public void setActive(boolean enabled, String actorId, Instant now) {
        this.active = enabled;
        this.version++;
        if (enabled) {
            this.grantedAt = now;
            this.grantedBy = actorId;
            this.revokedAt = null;
            this.revokedBy = null;
        } else {
            this.revokedAt = now;
            this.revokedBy = actorId;
        }
    }
}
