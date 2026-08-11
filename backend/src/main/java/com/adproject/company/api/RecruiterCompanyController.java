package com.adproject.company.api;

import com.adproject.common.security.AuthenticatedUser;
import com.adproject.company.api.CompanyDtos.CompanyResponse;
import com.adproject.company.api.CompanyDtos.UpdateCompanyRequest;
import com.adproject.company.application.RecruiterCompanyService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recruiter/company")
public class RecruiterCompanyController {
    private final RecruiterCompanyService companyService;

    public RecruiterCompanyController(RecruiterCompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    CompanyResponse get(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return companyService.get(currentUser);
    }

    @PatchMapping
    CompanyResponse update(@AuthenticationPrincipal AuthenticatedUser currentUser,
                           @Valid @RequestBody UpdateCompanyRequest request) {
        return companyService.update(currentUser, request);
    }
}
