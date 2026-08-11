package com.adproject.user.infrastructure;

import com.adproject.user.domain.UserRole;
import com.adproject.user.domain.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status;

    @Column(name = "accepted_terms_version", nullable = false, length = 100)
    private String acceptedTermsVersion;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private int version;

    protected UserEntity() {}

    public UserEntity(String id, String email, String passwordHash, String fullName, UserRole role,
                      UserStatus status, String acceptedTermsVersion, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.status = status;
        this.acceptedTermsVersion = acceptedTermsVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = 1;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public UserRole getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public String getAvatarUrl() { return avatarUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }

    public void updateFullName(String fullName, Instant now) {
        this.fullName = fullName;
        this.updatedAt = now;
        this.version++;
    }

    public void changeStatus(UserStatus status, Instant now) {
        this.status = status;
        touch(now);
    }

    public void touch(Instant now) {
        this.updatedAt = now;
        this.version++;
    }
}
