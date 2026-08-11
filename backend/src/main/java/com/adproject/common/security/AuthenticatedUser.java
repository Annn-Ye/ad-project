package com.adproject.common.security;

import com.adproject.user.domain.UserRole;

public record AuthenticatedUser(String userId, UserRole role, boolean platformAdmin) {}
