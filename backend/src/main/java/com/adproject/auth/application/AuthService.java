package com.adproject.auth.application;

import com.adproject.admin.infrastructure.AdminGrantRepository;
import com.adproject.auth.api.AuthResponses.AuthData;
import com.adproject.auth.api.AuthResponses.AuthResponse;
import com.adproject.auth.api.AuthResponses.AuthUser;
import com.adproject.auth.api.AuthResponses.Company;
import com.adproject.auth.api.AuthResponses.TokenData;
import com.adproject.auth.api.AuthResponses.TokenResponse;
import com.adproject.auth.api.LoginRequest;
import com.adproject.auth.api.RegisterRequest;
import com.adproject.auth.infrastructure.RefreshTokenRepository;
import com.adproject.common.api.ApiException;
import com.adproject.company.domain.CompanyMemberRole;
import com.adproject.company.domain.CompanyVerificationStatus;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyMemberEntity;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.domain.UserStatus;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final int ACCESS_SECONDS = 7200;
    private static final int REFRESH_SECONDS = 2592000;
    private static final String DUMMY_PASSWORD_HASH = "$2a$12$1jG8k9cHflH/WRpkV1vR9uPQ/B7f8OQMU1Q11nhiO8b.eHmssSf8a";

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AdminGrantRepository adminGrantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    public AuthService(UserRepository userRepository, CompanyRepository companyRepository,
                       CompanyMemberRepository memberRepository, RefreshTokenRepository refreshTokenRepository,
                       AdminGrantRepository adminGrantRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokenService, Clock clock) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.adminGrantRepository = adminGrantRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        UserRole role = parsePublicRole(request.role());
        validateRoleSpecificFields(role, request.companyName());
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw emailConflict();
        }
        Instant now = clock.instant();
        UserEntity user = new UserEntity(UUID.randomUUID().toString(), email,
                passwordEncoder.encode(request.password()), request.fullName(), role, UserStatus.ACTIVE,
                request.acceptedTermsVersion(), now, now);
        userRepository.saveAndFlush(user);

        CompanyEntity company = null;
        if (role == UserRole.RECRUITER) {
            company = new CompanyEntity(UUID.randomUUID().toString(), request.companyName(),
                    CompanyVerificationStatus.PENDING, 1, user.getId(), now, now);
            companyRepository.save(company);
            memberRepository.save(new CompanyMemberEntity(UUID.randomUUID().toString(), company.getId(), user.getId(),
                    CompanyMemberRole.ADMIN, now));
        }
        return authResponse(user, company);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        if (request.email() == null || request.password() == null) {
            throw unauthorized();
        }
        String email = normalizeEmail(request.email());
        UserEntity user = userRepository.findByEmail(email).orElse(null);
        String hash = user == null ? DUMMY_PASSWORD_HASH : user.getPasswordHash();
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (!matches || user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw unauthorized();
        }
        return authResponse(user, findCompany(user));
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        var existing = refreshTokenRepository.findByTokenHashForUpdate(refreshTokenService.digest(requireToken(rawRefreshToken)))
                .orElseThrow(AuthService::unauthorized);
        if (existing.getRevokedAt() != null || !existing.getExpiresAt().isAfter(now)) {
            throw unauthorized();
        }
        UserEntity user = userRepository.findById(existing.getUserId()).orElseThrow(AuthService::unauthorized);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw unauthorized();
        }
        var replacement = refreshTokenService.issue(user.getId());
        existing.revoke(now, replacement.entity().getId());
        return new TokenResponse(new TokenData(jwtService.createAccessToken(user), replacement.rawToken(),
                ACCESS_SECONDS, REFRESH_SECONDS));
    }

    @Transactional
    public void logout(String currentUserId, String rawRefreshToken) {
        Instant now = clock.instant();
        var existing = refreshTokenRepository.findByTokenHashForUpdate(refreshTokenService.digest(requireToken(rawRefreshToken)))
                .orElseThrow(AuthService::unauthorized);
        if (!existing.getUserId().equals(currentUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
        if (existing.getRevokedAt() != null || !existing.getExpiresAt().isAfter(now)) {
            throw unauthorized();
        }
        existing.revoke(now, null);
    }

    private AuthResponse authResponse(UserEntity user, CompanyEntity company) {
        var refresh = refreshTokenService.issue(user.getId());
        return new AuthResponse(new AuthData(jwtService.createAccessToken(user), refresh.rawToken(), ACCESS_SECONDS,
                REFRESH_SECONDS, toAuthUser(user, company)));
    }

    private CompanyEntity findCompany(UserEntity user) {
        if (user.getRole() == UserRole.CANDIDATE) {
            return null;
        }
        return memberRepository.findByUserId(user.getId())
                .flatMap(member -> companyRepository.findById(member.getCompanyId()))
                .orElseThrow(() -> new IllegalStateException("Recruiter company membership is missing"));
    }

    private AuthUser toAuthUser(UserEntity user, CompanyEntity company) {
        List<String> permissions = adminGrantRepository.existsByUserIdAndActiveTrue(user.getId())
                ? List.of("PLATFORM_ADMIN") : List.of();
        return new AuthUser(user.getId(), user.getRole().name(), user.getFullName(), user.getEmail(), user.getAvatarUrl(),
                user.getCreatedAt(), user.getUpdatedAt(), permissions, toCompany(company));
    }

    private Company toCompany(CompanyEntity company) {
        if (company == null) {
            return null;
        }
        return new Company(company.getId(), company.getName(), company.getLogoUrl(), company.getStage(),
                company.getEmployeeRange(), company.getVerificationStatus().name(), company.getWebsite(),
                company.getDescription(), company.getLocation(), company.getVersion(), company.getCreatedAt(),
                company.getUpdatedAt());
    }

    private static UserRole parsePublicRole(String rawRole) {
        try {
            UserRole role = UserRole.valueOf(rawRole);
            if (role != UserRole.CANDIDATE && role != UserRole.RECRUITER) throw new IllegalArgumentException();
            return role;
        } catch (RuntimeException exception) {
            throw validation("role", "must be CANDIDATE or RECRUITER");
        }
    }

    private static void validateRoleSpecificFields(UserRole role, String companyName) {
        if (role == UserRole.RECRUITER && (companyName == null || companyName.isBlank())) {
            throw validation("companyName", "must not be blank for RECRUITER");
        }
        if (role == UserRole.CANDIDATE && companyName != null) {
            throw validation("companyName", "is not allowed for CANDIDATE");
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw unauthorized();
        }
        return token;
    }

    private static ApiException validation(String field, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request validation failed",
                Map.of(field, detail));
    }

    private static ApiException emailConflict() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email is already registered");
    }

    private static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid email, password, or token");
    }
}
