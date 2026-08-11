package com.adproject.auth.api;

import java.time.Instant;
import java.util.List;

public final class AuthResponses {
    private AuthResponses() {}

    public record AuthResponse(AuthData data) {}
    public record TokenResponse(TokenData data) {}
    public record AuthData(String accessToken, String refreshToken, int expiresIn, int refreshExpiresIn,
                           AuthUser user) {}
    public record TokenData(String accessToken, String refreshToken, int expiresIn, int refreshExpiresIn) {}
    public record AuthUser(String userId, String role, String fullName, String email, String avatarUrl,
                           Instant createdAt, Instant updatedAt, List<String> permissions, Company company) {}
    public record Company(String companyId, String name, String logoUrl, String stage, String employeeRange,
                          String verificationStatus, String website, String description, String location,
                          int version, Instant createdAt, Instant updatedAt) {}
}
