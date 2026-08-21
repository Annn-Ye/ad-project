package com.adproject.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adproject.auth.application.JwtService;
import com.adproject.profile.infrastructure.CandidateProfileEntity;
import com.adproject.profile.infrastructure.CandidateProfileRepository;
import com.adproject.resume.infrastructure.ResumeEntity;
import com.adproject.resume.infrastructure.ResumeRepository;
import com.adproject.user.domain.UserRole;
import com.adproject.user.domain.UserStatus;
import com.adproject.user.infrastructure.UserEntity;
import com.adproject.user.infrastructure.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentRunIntegrationTest {
    private static final HttpServer PLANNER = startPlanner();

    @DynamicPropertySource
    static void agentProperties(DynamicPropertyRegistry registry) {
        registry.add("app.agent.planner-base-url",
                () -> "http://localhost:" + PLANNER.getAddress().getPort());
        registry.add("app.agent.preview-ttl-seconds", () -> 900);
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ResumeRepository resumes;
    @Autowired CandidateProfileRepository profiles;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @AfterAll
    static void stopPlanner() {
        PLANNER.stop(0);
    }

    @Test
    void createsOwnedReadOnlyAgePreviewAndPersistsSafeSteps() throws Exception {
        Account candidate = candidate(true, 27);
        String body = mvc.perform(post("/api/v1/agent/runs")
                        .header("Authorization", bearer(candidate))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"instruction":"把我默认简历里的年龄改成 28"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.data.confirmationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.target.type").value("RESUME"))
                .andExpect(jsonPath("$.data.target.id").value(candidate.resumeId()))
                .andExpect(jsonPath("$.data.preview.expectedVersion").value(1))
                .andExpect(jsonPath("$.data.preview.changes[0].field").value("age"))
                .andExpect(jsonPath("$.data.preview.changes[0].oldValue").value(27))
                .andExpect(jsonPath("$.data.preview.changes[0].newValue").value(28))
                .andExpect(jsonPath("$.data.steps.length()").value(3))
                .andExpect(jsonPath("$.data.steps[1].tool").value("get_my_resume"))
                .andExpect(jsonPath("$.data.steps[2].tool").value("preview_resume_patch"))
                .andReturn().getResponse().getContentAsString();

        JsonNode run = mapper.readTree(body).path("data");
        String runId = run.path("runId").asText();
        mvc.perform(get("/api/v1/agent/runs/{runId}", runId)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value(runId))
                .andExpect(jsonPath("$.data.preview.changes[0].newValue").value(28));

        ResumeEntity unchanged = resumes.findById(candidate.resumeId()).orElseThrow();
        assertThat(unchanged.getAge()).isEqualTo(27);
        assertThat(unchanged.getVersion()).isEqualTo(1);
        String audit = String.join(" ", jdbc.query(
                "select input_summary, output_summary from agent_steps where run_id=? order by sequence_no",
                (result, row) -> String.valueOf(result.getString(1)) + String.valueOf(result.getString(2)), runId));
        assertThat(audit).doesNotContain("Private resume summary").doesNotContain("Bearer ");
    }

    @Test
    void matchingAgeCompletesAsNoActionWithoutPreviewConfirmationOrResumeWrite() throws Exception {
        Account candidate = candidate(true, 28);
        JsonNode run = createRunNode(candidate, "把我默认简历里的年龄改成 28");

        assertThat(run.path("status").asText()).isEqualTo("NO_ACTION_REQUIRED");
        assertThat(run.path("confirmationStatus").asText()).isEqualTo("NOT_REQUIRED");
        assertThat(run.at("/target/type").asText()).isEqualTo("RESUME");
        assertThat(run.at("/target/id").asText()).isEqualTo(candidate.resumeId());
        assertThat(run.path("message").asText())
                .isEqualTo("Your default resume age is already 28, so no change is needed.");
        assertThat(run.path("preview").isNull()).isTrue();
        assertThat(run.path("result").isNull()).isTrue();

        ResumeEntity unchanged = resumes.findById(candidate.resumeId()).orElseThrow();
        assertThat(unchanged.getAge()).isEqualTo(28);
        assertThat(unchanged.getVersion()).isEqualTo(1);

        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", run.path("runId").asText())
                        .header("Authorization", bearer(candidate))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationId\":\"unused\",\"expectedRunVersion\":"
                                + run.path("version").asInt() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AGENT_RUN_NOT_CONFIRMABLE"));
        assertThat(resumes.findById(candidate.resumeId()).orElseThrow().getVersion()).isEqualTo(1);
    }

    @Test
    void enforcesRoleAndRunOwnershipAndCancellationNeverChangesResume() throws Exception {
        Account owner = candidate(true, 27);
        Account other = candidate(true, 30);
        String runId = createRun(owner, "年龄改成 28");

        mvc.perform(get("/api/v1/agent/runs/{runId}", runId)
                        .header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/agent/runs/{runId}/cancel", runId)
                        .header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/agent/runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction\":\"年龄改成 28\"}"))
                .andExpect(status().isUnauthorized());

        Account recruiter = account(UserRole.RECRUITER, false, 0);
        mvc.perform(post("/api/v1/agent/runs")
                        .header("Authorization", bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction\":\"年龄改成 28\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/agent/runs/{runId}/cancel", runId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.confirmationStatus").value("CANCELLED"));
        mvc.perform(post("/api/v1/agent/runs/{runId}/cancel", runId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertThat(resumes.findById(owner.resumeId()).orElseThrow().getAge()).isEqualTo(27);
    }

    @Test
    void clarificationAndMissingResumeProduceSafePersistedStates() throws Exception {
        Account candidate = candidate(true, 27);
        String clarificationId = createRun(candidate, "unsupported instruction");
        mvc.perform(get("/api/v1/agent/runs/{runId}", clarificationId)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEEDS_CLARIFICATION"))
                .andExpect(jsonPath("$.data.preview").isEmpty())
                .andExpect(jsonPath("$.data.steps.length()").value(1));

        Account withoutResume = candidate(false, 0);
        String failedId = createRun(withoutResume, "年龄改成 28");
        mvc.perform(get("/api/v1/agent/runs/{runId}", failedId)
                        .header("Authorization", bearer(withoutResume)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.data.preview").isEmpty());
    }

    @Test
    void rejectsPlannerAttemptsToUseAnyNonWhitelistedTool() throws Exception {
        Account candidate = candidate(true, 27);
        String runId = createRun(candidate, "malicious planner response");

        mvc.perform(get("/api/v1/agent/runs/{runId}", runId)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorCode").value("AGENT_PLAN_REJECTED"))
                .andExpect(jsonPath("$.data.preview").isEmpty());
        assertThat(resumes.findById(candidate.resumeId()).orElseThrow().getAge()).isEqualTo(27);
    }

    @Test
    void plannerFailureProducesASafePersistedFailureWithoutChangingResume() throws Exception {
        Account candidate = candidate(true, 27);
        String runId = createRun(candidate, "planner unavailable");

        mvc.perform(get("/api/v1/agent/runs/{runId}", runId)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorCode").value("AGENT_PLANNER_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.message").value(
                        "The AI service is busy or temporarily unavailable. Please try again in a moment."))
                .andExpect(jsonPath("$.data.steps[0].status").value("FAILED"));
        assertThat(resumes.findById(candidate.resumeId()).orElseThrow().getAge()).isEqualTo(27);
    }

    @Test
    void confirmedPreviewChangesOnlyAgeOnceAndReturnsTheOriginalResultForAnIdempotentReplay() throws Exception {
        Account candidate = candidate(true, 27);
        JsonNode created = createRunNode(candidate, "年龄改成 28");
        String runId = created.path("runId").asText();
        String confirmationId = created.at("/preview/confirmationId").asText();
        int expectedRunVersion = created.path("version").asInt();
        String key = UUID.randomUUID().toString();
        String request = mapper.writeValueAsString(java.util.Map.of(
                "confirmationId", confirmationId, "expectedRunVersion", expectedRunVersion));

        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", runId)
                        .header("Authorization", bearer(candidate))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.confirmationStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.result.operation").value("UPDATE_RESUME"))
                .andExpect(jsonPath("$.data.result.previousVersion").value(1))
                .andExpect(jsonPath("$.data.result.newVersion").value(2))
                .andExpect(jsonPath("$.data.steps[4].tool").value("apply_resume_patch"));

        ResumeEntity updated = resumes.findById(candidate.resumeId()).orElseThrow();
        assertThat(updated.getAge()).isEqualTo(28);
        assertThat(updated.getVersion()).isEqualTo(2);
        assertThat(updated.getSummary()).isEqualTo("Private resume summary");
        assertThat(profiles.findById(candidate.userId()).orElseThrow().getAge()).isEqualTo(28);

        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", runId)
                        .header("Authorization", bearer(candidate))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.result.newVersion").value(2));
        assertThat(resumes.findById(candidate.resumeId()).orElseThrow().getVersion()).isEqualTo(2);

        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", runId)
                        .header("Authorization", bearer(candidate))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AGENT_CONFIRMATION_ALREADY_USED"));
    }

    @Test
    void expiredOrStalePreviewAndInvalidConfirmationNeverApplyTheAgentChange() throws Exception {
        Account expiredCandidate = candidate(true, 27);
        JsonNode expired = createRunNode(expiredCandidate, "年龄改成 28");
        jdbc.update("update agent_runs set preview_expires_at=? where id=?",
                java.sql.Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")), expired.path("runId").asText());
        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", expired.path("runId").asText())
                        .header("Authorization", bearer(expiredCandidate))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(expired)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.confirmationStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.data.errorCode").value("AGENT_CONFIRMATION_EXPIRED"));
        assertThat(resumes.findById(expiredCandidate.resumeId()).orElseThrow().getAge()).isEqualTo(27);

        Account staleCandidate = candidate(true, 27);
        JsonNode stale = createRunNode(staleCandidate, "年龄改成 28");
        ResumeEntity externallyChanged = resumes.findById(staleCandidate.resumeId()).orElseThrow();
        externallyChanged.replace(externallyChanged.getFullName(), 30, externallyChanged.getLocation(),
                externallyChanged.getHeadline(), externallyChanged.getSummary(), externallyChanged.getExperiencesJson(),
                externallyChanged.getSkillsJson(),
                Instant.parse("2026-08-15T08:01:00Z"));
        resumes.saveAndFlush(externallyChanged);
        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", stale.path("runId").asText())
                        .header("Authorization", bearer(staleCandidate))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(stale)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorCode").value("VERSION_CONFLICT"));
        assertThat(resumes.findById(staleCandidate.resumeId()).orElseThrow().getAge()).isEqualTo(30);

        Account mismatchCandidate = candidate(true, 27);
        JsonNode mismatch = createRunNode(mismatchCandidate, "年龄改成 28");
        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", mismatch.path("runId").asText())
                        .header("Authorization", bearer(mismatchCandidate))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationId\":\"wrong\",\"expectedRunVersion\":"
                                + mismatch.path("version").asInt() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AGENT_CONFIRMATION_MISMATCH"));
        assertThat(resumes.findById(mismatchCandidate.resumeId()).orElseThrow().getAge()).isEqualTo(27);
    }

    @Test
    void confirmationRequiresOwnerCandidateHeaderAndCurrentRunVersionAndCannotFollowCancellation() throws Exception {
        Account owner = candidate(true, 27);
        Account other = candidate(true, 29);
        Account recruiter = account(UserRole.RECRUITER, false, 0);
        JsonNode created = createRunNode(owner, "年龄改成 28");
        String runId = created.path("runId").asText();

        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", runId)
                        .header("Authorization", bearer(other))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(created)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", runId)
                        .header("Authorization", bearer(recruiter))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(created)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", runId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(created)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.fieldErrors['Idempotency-Key']").exists());
        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", runId)
                        .header("Authorization", bearer(owner))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationId\":\"" + created.at("/preview/confirmationId").asText()
                                + "\",\"expectedRunVersion\":999}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AGENT_RUN_VERSION_CONFLICT"));

        mvc.perform(post("/api/v1/agent/runs/{runId}/cancel", runId)
                        .header("Authorization", bearer(owner))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", runId)
                        .header("Authorization", bearer(owner))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(created)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AGENT_RUN_NOT_CONFIRMABLE"));
        assertThat(resumes.findById(owner.resumeId()).orElseThrow().getAge()).isEqualTo(27);
    }

    @Test
    void queriesAndUpdatesSummaryWithoutChangingOtherResumeFields() throws Exception {
        Account candidate = candidate(true, 27);
        JsonNode query = createRunNode(candidate, "query summary");
        assertThat(query.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(query.path("confirmationStatus").asText()).isEqualTo("NOT_REQUIRED");
        assertThat(query.at("/result/queryResult/section").asText()).isEqualTo("summary");
        assertThat(query.at("/result/queryResult/summary").asText()).isEqualTo("Private resume summary");
        assertThat(query.path("preview").isNull()).isTrue();
        assertThat(resumes.findById(candidate.resumeId()).orElseThrow().getVersion()).isEqualTo(1);

        JsonNode preview = createRunNode(candidate, "set summary");
        assertThat(preview.at("/preview/changes/0/field").asText()).isEqualTo("summary");
        assertThat(preview.at("/preview/changes/0/oldValue").asText()).isEqualTo("Private resume summary");
        assertThat(preview.at("/preview/changes/0/newValue").asText()).isEqualTo("Updated agent summary");
        JsonNode completed = confirmRun(candidate, preview);
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        ResumeEntity saved = resumes.findById(candidate.resumeId()).orElseThrow();
        assertThat(saved.getSummary()).isEqualTo("Updated agent summary");
        assertThat(saved.getAge()).isEqualTo(27);
        assertThat(saved.getVersion()).isEqualTo(2);
    }

    @Test
    void addsRenamesDeletesAndQueriesSkillsWithConfirmationPerWrite() throws Exception {
        Account candidate = candidate(true, 27);

        confirmRun(candidate, createRunNode(candidate, "add skill"));
        assertThat(mapper.readTree(resumes.findById(candidate.resumeId()).orElseThrow().getSkillsJson()))
                .isEqualTo(mapper.readTree("[\"Python\",\"Spring\"]"));

        confirmRun(candidate, createRunNode(candidate, "rename skill"));
        assertThat(mapper.readTree(resumes.findById(candidate.resumeId()).orElseThrow().getSkillsJson()))
                .isEqualTo(mapper.readTree("[\"Python\",\"Spring Boot\"]"));

        confirmRun(candidate, createRunNode(candidate, "delete skill"));
        ResumeEntity saved = resumes.findById(candidate.resumeId()).orElseThrow();
        assertThat(mapper.readTree(saved.getSkillsJson())).isEqualTo(mapper.readTree("[\"Spring Boot\"]"));
        assertThat(saved.getVersion()).isEqualTo(4);

        JsonNode query = createRunNode(candidate, "query skills");
        assertThat(query.at("/result/queryResult/skills/0").asText()).isEqualTo("Spring Boot");
        assertThat(resumes.findById(candidate.resumeId()).orElseThrow().getVersion()).isEqualTo(4);
    }

    @Test
    void addsUpdatesDeletesAndQueriesExperiencesWithConfirmationPerWrite() throws Exception {
        Account candidate = candidate(true, 27);

        JsonNode addition = createRunNode(candidate, "add experience");
        assertThat(addition.at("/preview/changes/0/newValue/0/title").asText()).isEqualTo("Engineer");
        confirmRun(candidate, addition);
        JsonNode afterAdd = mapper.readTree(resumes.findById(candidate.resumeId()).orElseThrow().getExperiencesJson());
        assertThat(afterAdd.get(0).path("experienceId").asText()).isNotBlank();
        assertThat(afterAdd.get(0).path("description").asText()).isEqualTo("Built APIs");

        confirmRun(candidate, createRunNode(candidate, "update experience"));
        JsonNode afterUpdate = mapper.readTree(resumes.findById(candidate.resumeId()).orElseThrow().getExperiencesJson());
        assertThat(afterUpdate.get(0).path("description").asText()).isEqualTo("Built reliable APIs");

        JsonNode query = createRunNode(candidate, "query experiences");
        assertThat(query.at("/result/queryResult/experiences/0/company").asText()).isEqualTo("Acme");

        confirmRun(candidate, createRunNode(candidate, "delete experience"));
        ResumeEntity saved = resumes.findById(candidate.resumeId()).orElseThrow();
        assertThat(mapper.readTree(saved.getExperiencesJson()).isEmpty()).isTrue();
        assertThat(saved.getVersion()).isEqualTo(4);
    }

    @Test
    void persistsMultiTurnChatHistoryAndUsesTheOwnedConversationForFollowUp() throws Exception {
        Account candidate = candidate(true, 27);
        JsonNode greeting = createRunNode(candidate, "hello");
        String conversationId = greeting.path("conversationId").asText();
        assertThat(greeting.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(greeting.path("message").asText()).contains("Hello");
        assertThat(greeting.path("preview").isNull()).isTrue();

        JsonNode clarification = createRunNode(candidate, "ask age", conversationId);
        assertThat(clarification.path("status").asText()).isEqualTo("NEEDS_CLARIFICATION");
        JsonNode followUp = createRunNode(candidate, "28", conversationId);
        assertThat(followUp.path("status").asText()).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(followUp.at("/preview/changes/0/newValue").asInt()).isEqualTo(28);

        JsonNode secondConversation = createRunNode(candidate, "hello");
        String secondConversationId = secondConversation.path("conversationId").asText();
        assertThat(secondConversationId).isNotEqualTo(conversationId);

        String listBody = mvc.perform(get("/api/v1/agent/conversations")
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        List<String> listedIds = mapper.readTree(listBody).path("data").findValuesAsText("conversationId");
        assertThat(listedIds).containsExactlyInAnyOrder(conversationId, secondConversationId);

        mvc.perform(get("/api/v1/agent/conversations/recent")
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").isNotEmpty());

        mvc.perform(get("/api/v1/agent/conversations/{conversationId}", conversationId)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runs.length()").value(3))
                .andExpect(jsonPath("$.data.runs[0].instruction").value("hello"))
                .andExpect(jsonPath("$.data.runs[2].instruction").value("28"));

        Account other = candidate(true, 30);
        mvc.perform(get("/api/v1/agent/conversations/{conversationId}", conversationId)
                        .header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletesAnOwnedConversationAndHidesItFromOtherUsers() throws Exception {
        Account candidate = candidate(true, 27);
        JsonNode first = createRunNode(candidate, "hello");
        String conversationId = first.path("conversationId").asText();

        mvc.perform(delete("/api/v1/agent/conversations/{conversationId}", conversationId)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/agent/conversations")
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/agent/conversations/{conversationId}", conversationId)
                        .header("Authorization", bearer(candidate)))
                .andExpect(status().isNotFound());

        // Another candidate cannot delete (or even see) the conversation: safe 404.
        Account other = candidate(true, 30);
        mvc.perform(delete("/api/v1/agent/conversations/{conversationId}", conversationId)
                        .header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
    }

    private String createRun(Account account, String instruction) throws Exception {
        return createRunNode(account, instruction).path("runId").asText();
    }

    private JsonNode createRunNode(Account account, String instruction) throws Exception {
        return createRunNode(account, instruction, null);
    }

    private JsonNode createRunNode(Account account, String instruction, String conversationId) throws Exception {
        java.util.Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("instruction", instruction);
        if (conversationId != null) request.put("conversationId", conversationId);
        String body = mvc.perform(post("/api/v1/agent/runs")
                        .header("Authorization", bearer(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data");
    }

    private String confirmBody(JsonNode run) throws Exception {
        return mapper.writeValueAsString(java.util.Map.of(
                "confirmationId", run.at("/preview/confirmationId").asText(),
                "expectedRunVersion", run.path("version").asInt()));
    }

    private JsonNode confirmRun(Account account, JsonNode run) throws Exception {
        String body = mvc.perform(post("/api/v1/agent/runs/{runId}/confirm", run.path("runId").asText())
                        .header("Authorization", bearer(account))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(run)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data");
    }

    private Account candidate(boolean withResume, int age) {
        return account(UserRole.CANDIDATE, withResume, age);
    }

    private Account account(UserRole role, boolean withResume, int age) {
        Instant now = Instant.parse("2026-08-15T08:00:00Z");
        UserEntity user = users.save(new UserEntity(UUID.randomUUID().toString(),
                UUID.randomUUID() + "@example.com", "hash", "Agent User", role,
                UserStatus.ACTIVE, "2026-08", now, now));
        String resumeId = null;
        if (withResume) {
            resumeId = UUID.randomUUID().toString();
            profiles.save(new CandidateProfileEntity(user.getId(), "Engineer", "Shanghai", age,
                    null, null, null, 1, now, now));
            resumes.save(new ResumeEntity(resumeId, user.getId(), "Agent User", age, "Shanghai",
                    "Engineer", "Private resume summary", "[]", 1, now, now));
        }
        return new Account(jwt.createAccessToken(user), user.getId(), resumeId);
    }

    private static String bearer(Account account) {
        return "Bearer " + account.token();
    }

    private static HttpServer startPlanner() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/internal/v1/agent/plan", AgentRunIntegrationTest::respondWithPlan);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respondWithPlan(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (request.contains("planner unavailable")) {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
        }
        String response;
        if (request.contains("\"instruction\":\"hello\"")) {
            response = """
                  {"status":"CHAT","intent":"CHAT","target":null,"operations":[],
                   "message":"Hello! How can I help with your resume?"}
                  """;
        } else if (request.contains("\"instruction\":\"ask age\"")) {
            response = """
                  {"status":"NEEDS_CLARIFICATION","intent":null,"target":null,"operations":[],
                   "message":"What age should be set on your default resume?"}
                  """;
        } else if (request.contains("unsupported")) {
            response = """
                  {"status":"NEEDS_CLARIFICATION","intent":null,"target":null,"operations":[],
                   "message":"This instruction is not supported yet."}
                  """;
        } else if (request.contains("malicious")) {
            response = """
                  {"status":"READY","intent":"UPDATE_RESUME","target":"DEFAULT_RESUME","operations":[
                   {"tool":"disable_user","arguments":{}}],
                   "message":"Attempted unsafe plan."}
                  """;
        } else if (request.contains("query summary")) {
            response = queryPlan("summary");
        } else if (request.contains("query skills")) {
            response = queryPlan("skills");
        } else if (request.contains("query experiences")) {
            response = queryPlan("experiences");
        } else if (request.contains("set summary")) {
            response = updatePlan("""
                    {"field":"summary","action":"set","value":"Updated agent summary"}
                    """);
        } else if (request.contains("add skill")) {
            response = updatePlan("""
                    {"field":"skills","action":"add","values":["Python","Spring"]}
                    """);
        } else if (request.contains("rename skill")) {
            response = updatePlan("""
                    {"field":"skills","action":"update","oldValue":"Spring","newValue":"Spring Boot"}
                    """);
        } else if (request.contains("delete skill")) {
            response = updatePlan("""
                    {"field":"skills","action":"delete","values":["Python"]}
                    """);
        } else if (request.contains("add experience")) {
            response = updatePlan("""
                    {"field":"experiences","action":"add","experience":{"title":"Engineer","company":"Acme","description":"Built APIs","startDate":"2024-01","endDate":"2025-06"}}
                    """);
        } else if (request.contains("update experience")) {
            response = updatePlan("""
                    {"field":"experiences","action":"update","selector":"Engineer","changes":{"description":"Built reliable APIs"}}
                    """);
        } else if (request.contains("delete experience")) {
            response = updatePlan("""
                    {"field":"experiences","action":"delete","selector":"Engineer"}
                    """);
        } else {
            response = """
                  {"status":"READY","intent":"UPDATE_RESUME","target":"DEFAULT_RESUME","operations":[
                   {"tool":"get_my_resume","arguments":{}},
                   {"tool":"preview_resume_patch","arguments":{"field":"age","action":"set","value":28}}],
                   "message":"Review the proposed resume change."}
                  """;
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String queryPlan(String section) {
        return """
                {"status":"READY","intent":"QUERY_RESUME","target":"DEFAULT_RESUME","operations":[
                 {"tool":"get_my_resume","arguments":{}},
                 {"tool":"read_resume_section","arguments":{"section":"%s"}}],
                 "message":"Resume section loaded."}
                """.formatted(section);
    }

    private static String updatePlan(String arguments) {
        return """
                {"status":"READY","intent":"UPDATE_RESUME","target":"DEFAULT_RESUME","operations":[
                 {"tool":"get_my_resume","arguments":{}},
                 {"tool":"preview_resume_patch","arguments":%s}],
                 "message":"Review the proposed resume change."}
                """.formatted(arguments.trim());
    }

    private record Account(String token, String userId, String resumeId) {}
}
