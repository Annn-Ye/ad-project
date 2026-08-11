package com.adproject.company.api;

import com.adproject.company.domain.CompanyVerificationStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class CompanyDtos {
    private CompanyDtos() {}

    public record Company(String companyId, String name, String logoUrl, String stage, String employeeRange,
                          CompanyVerificationStatus verificationStatus, String website, String description,
                          String location, int version, Instant createdAt, Instant updatedAt) {}
    public record CompanyResponse(Company data) {}
    public record UpdateCompanyRequest(@Size(max = 200) String name,
                                       @Size(max = 500) String logoUrl,
                                       @Size(max = 32) String stage,
                                       @Size(max = 50) String employeeRange,
                                       @Size(max = 500) String website,
                                       String description,
                                       @Size(max = 100) String location,
                                       @NotNull @Min(1) Integer expectedVersion) {}
}
