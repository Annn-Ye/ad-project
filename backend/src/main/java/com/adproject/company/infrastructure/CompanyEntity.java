package com.adproject.company.infrastructure;

import com.adproject.company.domain.CompanyVerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "companies")
public class CompanyEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "logo_url", length = 500)
    private String logoUrl;
    @Column(length = 32)
    private String stage;
    @Column(name = "employee_range", length = 50)
    private String employeeRange;
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 32)
    private CompanyVerificationStatus verificationStatus;
    @Column(length = 500)
    private String website;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(length = 100)
    private String location;
    @Column(nullable = false)
    private int version;
    @Column(name = "created_by", nullable = false, length = 36, columnDefinition = "char(36)")
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CompanyEntity() {}

    public CompanyEntity(String id, String name, CompanyVerificationStatus verificationStatus, int version,
                         String createdBy, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.verificationStatus = verificationStatus;
        this.version = version;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getLogoUrl() { return logoUrl; }
    public String getStage() { return stage; }
    public String getEmployeeRange() { return employeeRange; }
    public CompanyVerificationStatus getVerificationStatus() { return verificationStatus; }
    public String getWebsite() { return website; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public int getVersion() { return version; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void changeVerificationStatus(CompanyVerificationStatus status, Instant now) {
        this.verificationStatus = status;
        this.updatedAt = now;
        this.version++;
    }

    public void updateProfile(String name, String logoUrl, String stage, String employeeRange,
                              String website, String description, String location, Instant now) {
        this.name = name;
        this.logoUrl = logoUrl;
        this.stage = stage;
        this.employeeRange = employeeRange;
        this.website = website;
        this.description = description;
        this.location = location;
        if (verificationStatus == CompanyVerificationStatus.CHANGES_REQUESTED) {
            verificationStatus = CompanyVerificationStatus.PENDING;
        }
        this.updatedAt = now;
        this.version++;
    }
}
