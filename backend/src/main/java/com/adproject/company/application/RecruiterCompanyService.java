package com.adproject.company.application;

import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.company.api.CompanyDtos.Company;
import com.adproject.company.api.CompanyDtos.CompanyResponse;
import com.adproject.company.api.CompanyDtos.UpdateCompanyRequest;
import com.adproject.company.domain.CompanyMemberRole;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.user.domain.UserRole;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecruiterCompanyService {
    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository memberRepository;
    private final Clock clock;

    public RecruiterCompanyService(CompanyRepository companyRepository, CompanyMemberRepository memberRepository,
                                   Clock clock) {
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CompanyResponse get(AuthenticatedUser currentUser) {
        requireRecruiter(currentUser);
        var member = memberRepository.findByUserId(currentUser.userId()).orElseThrow(RecruiterCompanyService::forbidden);
        return new CompanyResponse(toCompany(companyRepository.findById(member.getCompanyId())
                .orElseThrow(RecruiterCompanyService::notFound)));
    }

    @Transactional
    public CompanyResponse update(AuthenticatedUser currentUser, UpdateCompanyRequest request) {
        requireRecruiter(currentUser);
        var member = memberRepository.findByUserId(currentUser.userId()).orElseThrow(RecruiterCompanyService::forbidden);
        if (member.getMemberRole() != CompanyMemberRole.ADMIN) throw forbidden();
        CompanyEntity company = companyRepository.findByIdForUpdate(member.getCompanyId())
                .orElseThrow(RecruiterCompanyService::notFound);
        if (company.getVersion() != request.expectedVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "The company has changed; reload it before editing");
        }
        Map<String, String> errors = validate(request);
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "Request validation failed", errors);
        }
        company.updateProfile(value(request.name(), company.getName()), value(request.logoUrl(), company.getLogoUrl()),
                value(request.stage(), company.getStage()), value(request.employeeRange(), company.getEmployeeRange()),
                value(request.website(), company.getWebsite()), value(request.description(), company.getDescription()),
                value(request.location(), company.getLocation()), clock.instant());
        companyRepository.flush();
        return new CompanyResponse(toCompany(company));
    }

    private static Map<String, String> validate(UpdateCompanyRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (request.name() != null && request.name().isBlank()) errors.put("name", "must not be blank");
        if (request.website() != null && !request.website().isBlank()
                && !(request.website().startsWith("https://") || request.website().startsWith("http://"))) {
            errors.put("website", "must be an absolute http or https URL");
        }
        return errors;
    }

    private static String value(String candidate, String existing) {
        return candidate == null ? existing : candidate.trim();
    }

    private static Company toCompany(CompanyEntity company) {
        return new Company(company.getId(), company.getName(), company.getLogoUrl(), company.getStage(),
                company.getEmployeeRange(), company.getVerificationStatus(), company.getWebsite(),
                company.getDescription(), company.getLocation(), company.getVersion(), company.getCreatedAt(),
                company.getUpdatedAt());
    }

    private static void requireRecruiter(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.role() != UserRole.RECRUITER) throw forbidden();
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Company not found");
    }
}
