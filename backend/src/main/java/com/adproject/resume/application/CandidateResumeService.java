package com.adproject.resume.application;

import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.common.time.DatabaseTimePrecision;
import com.adproject.profile.infrastructure.CandidateProfileRepository;
import com.adproject.resume.api.ResumeDtos.Experience;
import com.adproject.resume.api.ResumeDtos.Resume;
import com.adproject.resume.api.ResumeDtos.ResumeResponse;
import com.adproject.resume.api.ResumeDtos.SaveResumeRequest;
import com.adproject.resume.infrastructure.ResumeEntity;
import com.adproject.resume.infrastructure.ResumeRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.infrastructure.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateResumeService {
    private static final TypeReference<List<Experience>> EXPERIENCES = new TypeReference<>() {};
    private static final TypeReference<List<String>> SKILLS = new TypeReference<>() {};

    private final ResumeRepository repository;
    private final UserRepository users;
    private final CandidateProfileRepository profiles;
    private final ObjectMapper mapper;
    private final Clock clock;

    public CandidateResumeService(ResumeRepository repository, UserRepository users,
                                  CandidateProfileRepository profiles, ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.users = users;
        this.profiles = profiles;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ResumeResponse get(AuthenticatedUser principal) {
        requireCandidate(principal);
        return response(repository.findByCandidateId(principal.userId())
                .orElseThrow(CandidateResumeService::notFound));
    }

    @Transactional
    public ResumeResponse save(AuthenticatedUser principal, SaveResumeRequest request) {
        requireCandidate(principal);
        validateContent(request.summary(), request.skills(), request.experiences());
        var existing = repository.findByCandidateIdForUpdate(principal.userId()).orElse(null);
        var user = users.findById(principal.userId()).orElseThrow();
        var profile = profiles.findById(principal.userId()).orElse(null);
        var now = DatabaseTimePrecision.micros(clock.instant());
        String fullName = user.getFullName();
        Integer age = profile != null && profile.getAge() != null
                ? profile.getAge() : existing != null ? existing.getAge() : null;
        if (age == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "Complete your profile (age) before saving a resume");
        }
        String location = profile != null && !profile.getLocation().isEmpty()
                ? profile.getLocation() : existing != null ? existing.getLocation() : "";
        String headline = profile != null && !profile.getHeadline().isEmpty()
                ? profile.getHeadline() : existing != null ? existing.getHeadline() : "";
        String experiencesJson = write(request.experiences());
        String skillsJson = write(normalizeSkills(request.skills()));
        if (existing == null) {
            if (request.expectedVersion() != 0) throw version();
            existing = repository.save(new ResumeEntity(UUID.randomUUID().toString(), principal.userId(), fullName,
                    age, location, headline, request.summary(), experiencesJson, skillsJson, 1, now, now));
        } else {
            if (existing.getVersion() != request.expectedVersion()) throw version();
            existing.replace(fullName, age, location, headline, request.summary(), experiencesJson, skillsJson, now);
        }
        repository.flush();
        return response(existing);
    }

    @Transactional
    public ResumeResponse patchAge(AuthenticatedUser principal, String resumeId, int age, int expectedVersion) {
        requireCandidate(principal);
        if (age < 16 || age > 100) {
            throw validation(Map.of("age", "must be between 16 and 100"));
        }
        var existing = ownedForUpdate(principal, resumeId, expectedVersion);
        var profile = profiles.findByUserIdForUpdate(principal.userId()).orElse(null);
        var now = DatabaseTimePrecision.micros(clock.instant());
        existing.replace(existing.getFullName(), age, existing.getLocation(), existing.getHeadline(),
                existing.getSummary(), existing.getExperiencesJson(), existing.getSkillsJson(),
                now);
        // Age is shared onboarding data. Keep the profile and resume consistent when the
        // Agent applies a confirmed resume-age change, without creating a partial profile.
        if (profile != null && (profile.getAge() == null || profile.getAge() != age)) {
            profile.update(profile.getHeadline(), profile.getLocation(), age, profile.getGender(),
                    profile.getPhone(), profile.getBirthplace(), now);
        }
        repository.flush();
        return response(existing);
    }

    @Transactional
    public ResumeResponse patchContent(AuthenticatedUser principal, String resumeId, int expectedVersion,
                                       String summary, List<String> skills, List<Experience> experiences) {
        requireCandidate(principal);
        validateContent(summary, skills, experiences);
        var existing = ownedForUpdate(principal, resumeId, expectedVersion);
        existing.replace(existing.getFullName(), existing.getAge(), existing.getLocation(), existing.getHeadline(),
                summary, write(experiences), write(normalizeSkills(skills)),
                DatabaseTimePrecision.micros(clock.instant()));
        repository.flush();
        return response(existing);
    }

    private ResumeEntity ownedForUpdate(AuthenticatedUser principal, String resumeId, int expectedVersion) {
        var existing = repository.findByIdForUpdate(resumeId)
                .filter(value -> value.getCandidateId().equals(principal.userId()))
                .orElseThrow(CandidateResumeService::notFound);
        if (existing.getVersion() != expectedVersion) throw version();
        return existing;
    }

    private ResumeResponse response(ResumeEntity entity) {
        return new ResumeResponse(new Resume(entity.getId(), entity.getFullName(), entity.getAge(),
                entity.getLocation(), entity.getHeadline(), entity.getSummary(), readStrings(entity.getSkillsJson()),
                readExperiences(entity.getExperiencesJson()), entity.getVersion(), entity.getCreatedAt(),
                entity.getUpdatedAt()));
    }

    private void validateContent(String summary, List<String> skills, List<Experience> experiences) {
        if (summary == null) throw validation(Map.of("summary", "is required"));
        if (skills == null) skills = List.of();
        if (skills.size() > 100) throw validation(Map.of("skills", "must contain at most 100 items"));
        for (String skill : skills) {
            if (skill == null || skill.isBlank() || skill.trim().length() > 200) {
                throw validation(Map.of("skills", "each skill must contain 1 to 200 characters"));
            }
        }
        if (experiences == null) throw validation(Map.of("experiences", "is required"));
        for (int index = 0; index < experiences.size(); index++) {
            Experience experience = experiences.get(index);
            String prefix = "experiences[" + index + "].";
            if (experience == null) throw validation(Map.of("experiences[" + index + "]", "is required"));
            if (experience.title() == null) {
                throw validation(Map.of(prefix + "title", "is required"));
            }
            if (experience.company() == null) {
                throw validation(Map.of(prefix + "company", "is required"));
            }
            if (experience.description() == null) {
                throw validation(Map.of(prefix + "description", "is required"));
            }
            yearMonth(experience.startDate(), prefix + "startDate", false);
            yearMonth(experience.endDate(), prefix + "endDate", true);
        }
    }

    private YearMonth yearMonth(String value, String field, boolean optional) {
        if (optional && (value == null || value.isBlank())) return null;
        try {
            if (value == null || !value.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
                throw new DateTimeParseException("invalid", value == null ? "" : value, 0);
            }
            return YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            throw validation(Map.of(field, "must use YYYY-MM"));
        }
    }

    private List<String> normalizeSkills(List<String> values) {
        if (values == null) return List.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) normalized.add(value.trim());
        return new ArrayList<>(normalized);
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private List<Experience> readExperiences(String value) {
        try {
            return mapper.readValue(value, EXPERIENCES);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private List<String> readStrings(String value) {
        try {
            return mapper.readValue(value, SKILLS);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void requireCandidate(AuthenticatedUser principal) {
        if (principal == null || principal.role() != UserRole.CANDIDATE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resume not found");
    }

    private static ApiException version() {
        return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "The resume has changed");
    }

    private static ApiException validation(Map<String, String> fields) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                "Request validation failed", fields);
    }
}
