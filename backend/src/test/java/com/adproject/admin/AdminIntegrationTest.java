package com.adproject.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.adproject.admin.application.ModerationIntakeService;
import com.adproject.admin.domain.ModerationSourceType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ModerationIntakeService moderationIntakeService;

    @Test
    void adminUserManagementIsAuthorizedVersionedAuditedAndRevokesTokens() throws Exception {
        Session admin = createAdmin("admin-users");
        Session target = registerCandidate("managed-user");

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.userId == '%s')]".formatted(target.userId())).exists());

        mockMvc.perform(post("/api/v1/admin/users/{id}/status", target.userId())
                        .header("Authorization", bearer(admin.accessToken()))
                        .header("X-Request-Id", "req_disable_user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\",\"reason\":\"Security review\",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(target.refreshToken())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/candidate/profile")
                        .header("Authorization", bearer(target.accessToken())))
                .andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject("select count(*) from admin_audit_events " +
                "where target_id = ? and action = 'USER_STATUS_CHANGED' and request_id = 'req_disable_user'",
                Integer.class, target.userId())).isEqualTo(1);

        mockMvc.perform(post("/api/v1/admin/users/{id}/admin-access", target.userId())
                        .header("Authorization", bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"reason\":\"Operations coverage\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminAccess").value(true))
                .andExpect(jsonPath("$.data.role").value("CANDIDATE"));

        mockMvc.perform(post("/api/v1/admin/users/{id}/status", admin.userId())
                        .header("Authorization", bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\",\"reason\":\"Self test\",\"expectedVersion\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SELF_ADMIN_CHANGE_NOT_ALLOWED"));
    }

    @Test
    void normalUserCannotAccessAdminApisAndReasonIsRequired() throws Exception {
        Session normal = registerCandidate("not-admin");
        Session admin = createAdmin("validation-admin");
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(normal.accessToken())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/users/{id}/status", normal.userId())
                        .header("Authorization", bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\",\"reason\":\"\",\"expectedVersion\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.fieldErrors.reason").exists());
    }

    @Test
    void companyReviewAndRecruiterRemediationFollowTheDeclaredStateMachine() throws Exception {
        Session admin = createAdmin("company-admin");
        Session recruiter = registerRecruiter("review-company");

        mockMvc.perform(post("/api/v1/admin/companies/{id}/request-changes", recruiter.companyId())
                        .header("Authorization", bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Add a public website\",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(patch("/api/v1/recruiter/company")
                        .header("Authorization", bearer(recruiter.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"website\":\"https://example.test\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.version").value(3));

        mockMvc.perform(post("/api/v1/admin/companies/{id}/approve", recruiter.companyId())
                        .header("Authorization", bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Website verified\",\"expectedVersion\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("APPROVED"));

        mockMvc.perform(post("/api/v1/admin/companies/{id}/reject", recruiter.companyId())
                        .header("Authorization", bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Second decision\",\"expectedVersion\":4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_COMPANY_REVIEW_TRANSITION"));
    }

    @Test
    void moderationFixtureCanBeReviewedOnceAndAppearsInAuditLog() throws Exception {
        Session admin = createAdmin("moderation-admin");
        Session author = registerCandidate("content-author");
        String sourceId = UUID.randomUUID().toString();
        var created = moderationIntakeService.report(ModerationSourceType.COMMUNITY_POST, sourceId, author.userId(),
                "A reported community post used for the integration test.", "Possible harassment");
        moderationIntakeService.report(ModerationSourceType.COMMUNITY_POST, sourceId, author.userId(),
                "A reported community post used for the integration test.", "Possible harassment");
        moderationIntakeService.report(ModerationSourceType.COMMUNITY_POST, sourceId, author.userId(),
                "A reported community post used for the integration test.", "Possible harassment");
        String caseId = created.caseId();
        assertThat(moderationIntakeService.isRemoved(ModerationSourceType.COMMUNITY_POST, sourceId)).isFalse();

        mockMvc.perform(get("/api/v1/admin/moderation/cases")
                        .param("status", "PENDING").header("Authorization", bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.caseId == '%s')].reportCount".formatted(caseId)).value(3));

        mockMvc.perform(post("/api/v1/admin/moderation/cases/{id}/decision", caseId)
                        .header("Authorization", bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REMOVE\",\"reason\":\"Violates community rules\",\"expectedVersion\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REMOVED"))
                .andExpect(jsonPath("$.data.version").value(4));
        assertThat(moderationIntakeService.isRemoved(ModerationSourceType.COMMUNITY_POST, sourceId)).isTrue();
        mockMvc.perform(post("/api/v1/admin/moderation/cases/{id}/decision", caseId)
                        .header("Authorization", bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"KEEP\",\"reason\":\"Retry\",\"expectedVersion\":4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_MODERATION_TRANSITION"));
        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .param("targetType", "MODERATION_CASE")
                        .header("Authorization", bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.targetId == '%s')].action".formatted(caseId)).value("MODERATION_REMOVED"));
    }

    private Session createAdmin(String prefix) throws Exception {
        Session registered = registerCandidate(prefix);
        Instant now = Instant.now();
        jdbcTemplate.update("insert into admin_grants (user_id, active, version, granted_at, granted_by) " +
                "values (?, true, 1, ?, ?)", registered.userId(), now, registered.userId());
        jdbcTemplate.update("update users set version = 2 where id = ?", registered.userId());
        String login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"StrongPass123!\"}".formatted(registered.email())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.permissions[0]").value("PLATFORM_ADMIN"))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(login).path("data");
        return new Session(registered.userId(), registered.email(), data.path("accessToken").asText(),
                data.path("refreshToken").asText(), null);
    }

    private Session registerCandidate(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        String response = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"CANDIDATE","fullName":"Admin Test User","email":"%s",
                                 "password":"StrongPass123!","acceptedTermsVersion":"2026-08"}
                                """.formatted(email)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        return new Session(data.at("/user/userId").asText(), email, data.path("accessToken").asText(),
                data.path("refreshToken").asText(), null);
    }

    private Session registerRecruiter(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        String response = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"RECRUITER","companyName":"Review Labs","fullName":"Review Recruiter",
                                 "email":"%s","password":"StrongPass123!","acceptedTermsVersion":"2026-08"}
                                """.formatted(email)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        return new Session(data.at("/user/userId").asText(), email, data.path("accessToken").asText(),
                data.path("refreshToken").asText(), data.at("/user/company/companyId").asText());
    }

    private static String bearer(String token) { return "Bearer " + token; }
    private record Session(String userId, String email, String accessToken, String refreshToken, String companyId) {}
}
