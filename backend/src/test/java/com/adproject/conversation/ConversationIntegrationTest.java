package com.adproject.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.adproject.auth.application.JwtService;
import com.adproject.company.domain.*;
import com.adproject.company.infrastructure.*;
import com.adproject.job.domain.*;
import com.adproject.job.infrastructure.*;
import com.adproject.resume.infrastructure.*;
import com.adproject.user.domain.*;
import com.adproject.user.infrastructure.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ConversationIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JwtService jwt; @Autowired UserRepository users;
    @Autowired CompanyRepository companies; @Autowired CompanyMemberRepository members;
    @Autowired JobRepository jobs; @Autowired ResumeRepository resumes; @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test void submitAutoCreatesUniqueConversation() throws Exception {
        Fixture f = fixture("Provision Candidate");
        String jobId = job(f, "Provision Job");
        String key = UUID.randomUUID().toString();
        String applicationId = submit(f, jobId, key);

        assertThat(jdbc.queryForObject("select count(*) from conversations where application_id=?",
                Integer.class, applicationId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select job_id from conversations where application_id=?",
                String.class, applicationId)).isEqualTo(jobId);
        assertThat(jdbc.queryForObject("select candidate_id from conversations where application_id=?",
                String.class, applicationId)).isEqualTo(f.candidateId());
        assertThat(jdbc.queryForObject("select company_id from conversations where application_id=?",
                String.class, applicationId)).isEqualTo(f.companyId());

        // Idempotent replay of the same submission must not create a second conversation.
        String replayApplicationId = submit(f, jobId, key);
        assertThat(replayApplicationId).isEqualTo(applicationId);
        assertThat(jdbc.queryForObject("select count(*) from conversations where application_id=?",
                Integer.class, applicationId)).isEqualTo(1);
    }

    @Test void candidateListsAndReadsOwnConversation() throws Exception {
        Fixture f = fixture("List Candidate");
        String jobId = job(f, "Backend Engineer");
        String applicationId = submit(f, jobId, UUID.randomUUID().toString());
        String conversationId = conversationId(applicationId);

        mvc.perform(get("/api/v1/candidate/conversations").header("Authorization", candidate(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].conversationId").value(conversationId))
                .andExpect(jsonPath("$.data[0].jobTitle").value("Backend Engineer"))
                .andExpect(jsonPath("$.data[0].unreadCount").value(0))
                .andExpect(jsonPath("$.data[0].participant.fullName").value("Recruiter"))
                .andExpect(jsonPath("$.data[0].participant.company.name").value("Company"))
                .andExpect(jsonPath("$.data[0].lastMessage").isEmpty());

        mvc.perform(get("/api/v1/candidate/conversations/{id}", conversationId).header("Authorization", candidate(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value(conversationId))
                .andExpect(jsonPath("$.data.participant.fullName").value("Recruiter"))
                .andExpect(jsonPath("$.data.context").isEmpty());
    }

    @Test void candidateSendsMessageAndRecruiterSeesUnread() throws Exception {
        Fixture f = fixture("Send Candidate");
        String jobId = job(f, "Send Job");
        String applicationId = submit(f, jobId, UUID.randomUUID().toString());
        String conversationId = conversationId(applicationId);

        String messageId = send(candidate(f), conversationId, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), "Hello recruiter");
        assertThat(messageId).isNotBlank();

        mvc.perform(get("/api/v1/candidate/conversations/{id}/messages", conversationId)
                        .header("Authorization", candidate(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].messageId").value(messageId))
                .andExpect(jsonPath("$.data[0].body").value("Hello recruiter"))
                .andExpect(jsonPath("$.data[0].senderType").value("CANDIDATE"))
                .andExpect(jsonPath("$.data[0].deliveryStatus").value("SENT"))
                .andExpect(jsonPath("$.meta.hasMore").value(false))
                .andExpect(jsonPath("$.meta.nextCursor").isEmpty());

        mvc.perform(get("/api/v1/recruiter/conversations").header("Authorization", recruiter(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].unreadCount").value(1))
                .andExpect(jsonPath("$.data[0].lastMessage.messageId").value(messageId));
        mvc.perform(get("/api/v1/candidate/conversations").header("Authorization", candidate(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].unreadCount").value(0));
    }

    @Test void candidateCanStartOneInquiryForAnActivePublicJobAndSendMessage() throws Exception {
        Fixture f = fixture("Candidate Inquiry");
        String jobId = job(f, "Inquiry Job");
        String first = mvc.perform(post("/api/v1/candidate/conversations/job/{jobId}", jobId)
                        .header("Authorization", candidate(f)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.conversationType").value("CANDIDATE_INQUIRY"))
                .andReturn().getResponse().getContentAsString();
        String conversationId = mapper.readTree(first).at("/data/conversationId").asText();

        mvc.perform(post("/api/v1/candidate/conversations/job/{jobId}", jobId)
                        .header("Authorization", candidate(f)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.conversationId").value(conversationId));
        assertThat(jdbc.queryForObject("select count(*) from conversations where id=? and application_id is null "
                + "and conversation_type='CANDIDATE_INQUIRY'", Integer.class, conversationId)).isEqualTo(1);

        String messageId = send(candidate(f), conversationId, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), "Can I ask a question about this role?");
        mvc.perform(get("/api/v1/recruiter/conversations/{id}/messages", conversationId)
                        .header("Authorization", recruiter(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].messageId").value(messageId));
    }

    @Test void recruiterSendsMessageWithoutAnyGoogleConnection() throws Exception {
        Fixture f = fixture("Recruiter Send Candidate");
        String applicationId = submit(f, job(f, "Recruiter Send Job"), UUID.randomUUID().toString());
        String conversationId = conversationId(applicationId);

        String response = mvc.perform(post("/api/v1/recruiter/conversations/{id}/messages", conversationId)
                        .header("Authorization", recruiter(f))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Thanks for applying\",\"clientMessageId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.senderType").value("RECRUITER"))
                .andExpect(jsonPath("$.data.body").value("Thanks for applying"))
                .andReturn().getResponse().getContentAsString();

        String messageId = mapper.readTree(response).at("/data/messageId").asText();
        mvc.perform(get("/api/v1/candidate/conversations/{id}/messages", conversationId)
                        .header("Authorization", candidate(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].messageId").value(messageId))
                .andExpect(jsonPath("$.data[0].deliveryStatus").value("SENT"));
    }

    @Test void recruiterCanStartOneIdempotentOutreachOnlyForOwnCompletedScreeningResult() throws Exception {
        Fixture f = fixture("Agent outreach");
        String jobId = job(f, "Talent Pool Job");
        String runId = UUID.randomUUID().toString();
        jdbc.update("insert into agent_runs (id,user_id,conversation_id,job_id,instruction,target_type,target_id,"
                        + "status,confirmation_status,result_json,version,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                runId, f.recruiterId(), UUID.randomUUID().toString(), jobId, "Screen candidates", "JOB", jobId,
                "COMPLETED", "NOT_REQUIRED", "{\"jobId\":\"" + jobId + "\",\"jobTitle\":\"Talent Pool Job\","
                        + "\"ranked\":[{\"candidateId\":\"" + f.candidateId() + "\",\"applicationId\":null,"
                        + "\"fullName\":\"Candidate\",\"applicationStatus\":null,\"rank\":1,"
                        + "\"strongMatches\":[],\"gaps\":[],\"recommendation\":\"Strong fit\"}],"
                        + "\"message\":\"Done\"}", 1, Instant.now(), Instant.now());

        String endpoint = "/api/v1/agent/runs/{runId}/ranked-candidates/{candidateId}/conversation";
        String first = mvc.perform(post(endpoint, runId, f.candidateId()).header("Authorization", recruiter(f)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.conversationId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String conversationId = mapper.readTree(first).at("/data/conversationId").asText();

        mvc.perform(post("/api/v1/recruiter/conversations/{id}/messages", conversationId)
                        .header("Authorization", recruiter(f)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Hello from Talent Pool\",\"clientMessageId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/candidate/conversations/{id}/messages", conversationId)
                        .header("Authorization", candidate(f)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].body").value("Hello from Talent Pool"));

        mvc.perform(post(endpoint, runId, f.candidateId()).header("Authorization", recruiter(f)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.conversationId").value(conversationId));
        assertThat(jdbc.queryForObject("select count(*) from conversations where id=? and application_id is null "
                + "and conversation_type='RECRUITER_OUTREACH'", Integer.class, conversationId)).isEqualTo(1);

        mvc.perform(post(endpoint, runId, UUID.randomUUID().toString()).header("Authorization", recruiter(f)))
                .andExpect(status().isNotFound());
    }

    @Test void conversationIsCompanyScoped() throws Exception {
        Fixture a = fixture("Company A Candidate");
        String jobId = job(a, "A Job");
        String applicationId = submit(a, jobId, UUID.randomUUID().toString());
        String conversationId = conversationId(applicationId);

        Fixture b = fixture("Company B Candidate");
        mvc.perform(get("/api/v1/recruiter/conversations/{id}", conversationId).header("Authorization", recruiter(b)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/recruiter/conversations").header("Authorization", recruiter(b)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/recruiter/conversations").header("Authorization", recruiter(a)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test void recruiterFindsUniqueConversationByApplicationId() throws Exception {
        Fixture f = fixture("Lookup Candidate");
        String jobA = job(f, "Lookup Job A");
        String jobB = job(f, "Lookup Job B");
        String appA = submit(f, jobA, UUID.randomUUID().toString());
        submit(f, jobB, UUID.randomUUID().toString());
        String convA = conversationId(appA);

        mvc.perform(get("/api/v1/recruiter/conversations")
                        .header("Authorization", recruiter(f))
                        .param("applicationId", appA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].conversationId").value(convA))
                .andExpect(jsonPath("$.data[0].applicationId").value(appA));
    }

    @Test void listWithoutApplicationIdPreservesExistingBehavior() throws Exception {
        Fixture f = fixture("Preserve List Candidate");
        String jobA = job(f, "Preserve Job A");
        String jobB = job(f, "Preserve Job B");
        submit(f, jobA, UUID.randomUUID().toString());
        submit(f, jobB, UUID.randomUUID().toString());

        mvc.perform(get("/api/v1/recruiter/conversations").header("Authorization", recruiter(f)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.total").value(2));
    }

    @Test void applicationIdDoesNotLeakCrossCompanyOrMissingApplication() throws Exception {
        Fixture a = fixture("Cross Lookup A");
        String appA = submit(a, job(a, "Cross A Job"), UUID.randomUUID().toString());

        Fixture b = fixture("Cross Lookup B");
        mvc.perform(get("/api/v1/recruiter/conversations")
                        .header("Authorization", recruiter(b))
                        .param("applicationId", appA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.meta.total").value(0));

        mvc.perform(get("/api/v1/recruiter/conversations")
                        .header("Authorization", recruiter(a))
                        .param("applicationId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.meta.total").value(0));
    }

    @Test void applicationIdRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/recruiter/conversations")
                        .param("applicationId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test void applicationIdRejectsCandidateRole() throws Exception {
        Fixture f = fixture("Role Lookup Candidate");
        mvc.perform(get("/api/v1/recruiter/conversations")
                        .header("Authorization", candidate(f))
                        .param("applicationId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test void applicationIdRejectsInvalidUuid() throws Exception {
        Fixture f = fixture("Invalid Uuid Candidate");
        mvc.perform(get("/api/v1/recruiter/conversations")
                        .header("Authorization", recruiter(f))
                        .param("applicationId", "not-a-uuid"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test void roleAndAuthEnforcement() throws Exception {
        Fixture f = fixture("Role Candidate");
        String jobId = job(f, "Role Job");
        String applicationId = submit(f, jobId, UUID.randomUUID().toString());
        String conversationId = conversationId(applicationId);

        mvc.perform(get("/api/v1/candidate/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/candidate/conversations/job/{jobId}", jobId))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/candidate/conversations/job/{jobId}", jobId)
                        .header("Authorization", recruiter(f)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/candidate/conversations/job/{jobId}", UUID.randomUUID())
                        .header("Authorization", candidate(f)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/recruiter/conversations").header("Authorization", candidate(f)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/candidate/conversations").header("Authorization", recruiter(f)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/recruiter/conversations/{id}/messages", conversationId)
                .header("Authorization", candidate(f)).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"x\",\"clientMessageId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void sendIsIdempotentAndDeduplicatesClientMessageId() throws Exception {
        Fixture f = fixture("Idempotent Candidate");
        String jobId = job(f, "Idempotent Job");
        String applicationId = submit(f, jobId, UUID.randomUUID().toString());
        String conversationId = conversationId(applicationId);

        String key = UUID.randomUUID().toString();
        String clientMessageId = UUID.randomUUID().toString();
        String first = send(candidate(f), conversationId, key, clientMessageId, "Hello");
        assertThat(first).isNotBlank();

        String replay = send(candidate(f), conversationId, key, clientMessageId, "Hello");
        assertThat(replay).isEqualTo(first);

        String dedup = send(candidate(f), conversationId, UUID.randomUUID().toString(), clientMessageId, "Hello");
        assertThat(dedup).isEqualTo(first);

        mvc.perform(post("/api/v1/candidate/conversations/{id}/messages", conversationId)
                        .header("Authorization", candidate(f)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Different\",\"clientMessageId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(jdbc.queryForObject("select count(*) from messages where conversation_id=?",
                Integer.class, conversationId)).isEqualTo(1);
    }

    @Test void closedConversationRejectsSend() throws Exception {
        Fixture f = fixture("Closed Candidate");
        String jobId = job(f, "Closed Job");
        String applicationId = submit(f, jobId, UUID.randomUUID().toString());
        String conversationId = conversationId(applicationId);
        jdbc.update("update applications set status='REJECTED',version=2 where id=?", applicationId);

        mvc.perform(post("/api/v1/candidate/conversations/{id}/messages", conversationId)
                        .header("Authorization", candidate(f)).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Too late\",\"clientMessageId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CONVERSATION_CLOSED"));
    }

    @Test void sendRequiresValidIdempotencyKey() throws Exception {
        Fixture f = fixture("Idem Required Candidate");
        String jobId = job(f, "Idem Required Job");
        String applicationId = submit(f, jobId, UUID.randomUUID().toString());
        String conversationId = conversationId(applicationId);
        String body = "{\"body\":\"Hello\",\"clientMessageId\":\"" + UUID.randomUUID() + "\"}";

        mvc.perform(post("/api/v1/candidate/conversations/{id}/messages", conversationId)
                        .header("Authorization", candidate(f)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/candidate/conversations/{id}/messages", conversationId)
                        .header("Authorization", candidate(f)).header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/recruiter/conversations/{id}/messages", conversationId)
                        .header("Authorization", recruiter(f)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/recruiter/conversations/{id}/messages", conversationId)
                        .header("Authorization", recruiter(f)).header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test void readStateIsPerUser() throws Exception {
        Fixture f = fixtureWithTwoRecruiters("Read State Candidate");
        String jobId = job(f, "Read State Job");
        String applicationId = submit(f, jobId, UUID.randomUUID().toString());
        String conversationId = conversationId(applicationId);
        String messageId = send(candidate(f), conversationId, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), "Please review");

        mvc.perform(get("/api/v1/recruiter/conversations").header("Authorization", recruiter(f)))
                .andExpect(jsonPath("$.data[0].unreadCount").value(1));
        mvc.perform(get("/api/v1/recruiter/conversations").header("Authorization", secondRecruiter(f)))
                .andExpect(jsonPath("$.data[0].unreadCount").value(1));

        mvc.perform(put("/api/v1/recruiter/conversations/{id}/read-state", conversationId)
                        .header("Authorization", recruiter(f)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadMessageId\":\"" + messageId + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/recruiter/conversations").header("Authorization", recruiter(f)))
                .andExpect(jsonPath("$.data[0].unreadCount").value(0));
        mvc.perform(get("/api/v1/recruiter/conversations").header("Authorization", secondRecruiter(f)))
                .andExpect(jsonPath("$.data[0].unreadCount").value(1));
    }

    // ---- helpers ----

    private String submit(Fixture f, String jobId, String idempotencyKey) throws Exception {
        String response = mvc.perform(post("/api/v1/jobs/{id}/applications", jobId)
                        .header("Authorization", candidate(f)).header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resumeId\":\"" + f.resumeId() +
                                "\",\"contactEmail\":\"" + f.email() + "\",\"shareProfile\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).at("/data/applicationId").asText();
    }

    private String send(String token, String conversationId, String key, String clientMessageId, String body)
            throws Exception {
        String response = mvc.perform(post("/api/v1/candidate/conversations/{id}/messages", conversationId)
                        .header("Authorization", token).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"" + body + "\",\"clientMessageId\":\"" + clientMessageId + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).at("/data/messageId").asText();
    }

    private String conversationId(String applicationId) {
        return jdbc.queryForObject("select id from conversations where application_id=?", String.class, applicationId);
    }

    private String job(Fixture f, String title) {
        Instant now = Instant.parse("2026-08-13T02:00:00Z");
        String id = UUID.randomUUID().toString();
        jobs.save(new JobEntity(id, f.companyId(), f.recruiterId(), f.recruiterId(), title, EmploymentType.FULL_TIME,
                WorkplaceType.HYBRID, "Singapore", 5000, 8000, SalaryCurrency.SGD, SalaryPeriod.MONTH, "Description",
                "[]", "[]", null, Visibility.PUBLIC, JobStatus.ACTIVE, 0, 1, now, now));
        return id;
    }

    private Fixture fixture(String candidateName) {
        Instant now = Instant.parse("2026-08-13T02:00:00Z");
        UserEntity recruiter = users.save(user("Recruiter", UserRole.RECRUITER, now));
        CompanyEntity company = companies.save(new CompanyEntity(UUID.randomUUID().toString(), "Company",
                CompanyVerificationStatus.APPROVED, 1, recruiter.getId(), now, now));
        members.save(new CompanyMemberEntity(UUID.randomUUID().toString(), company.getId(), recruiter.getId(), CompanyMemberRole.ADMIN, now));
        UserEntity candidate = users.save(user(candidateName, UserRole.CANDIDATE, now));
        String resumeId = UUID.randomUUID().toString();
        resumes.save(new ResumeEntity(resumeId, candidate.getId(), candidateName, 28, "Singapore", "Engineer", "Summary",
                "[]", 1, now, now));
        return new Fixture(jwt.createAccessToken(recruiter), jwt.createAccessToken(candidate), null,
                recruiter.getId(), candidate.getId(), candidate.getEmail(), company.getId(), resumeId);
    }

    private Fixture fixtureWithTwoRecruiters(String candidateName) {
        Instant now = Instant.parse("2026-08-13T02:00:00Z");
        UserEntity recruiter = users.save(user("Recruiter", UserRole.RECRUITER, now));
        CompanyEntity company = companies.save(new CompanyEntity(UUID.randomUUID().toString(), "Company",
                CompanyVerificationStatus.APPROVED, 1, recruiter.getId(), now, now));
        members.save(new CompanyMemberEntity(UUID.randomUUID().toString(), company.getId(), recruiter.getId(), CompanyMemberRole.ADMIN, now));
        UserEntity second = users.save(user("Second Recruiter", UserRole.RECRUITER, now));
        members.save(new CompanyMemberEntity(UUID.randomUUID().toString(), company.getId(), second.getId(), CompanyMemberRole.ADMIN, now));
        UserEntity candidate = users.save(user(candidateName, UserRole.CANDIDATE, now));
        String resumeId = UUID.randomUUID().toString();
        resumes.save(new ResumeEntity(resumeId, candidate.getId(), candidateName, 28, "Singapore", "Engineer", "Summary",
                "[]", 1, now, now));
        return new Fixture(jwt.createAccessToken(recruiter), jwt.createAccessToken(candidate),
                jwt.createAccessToken(second), recruiter.getId(), candidate.getId(), candidate.getEmail(),
                company.getId(), resumeId);
    }

    private UserEntity user(String name, UserRole role, Instant now) {
        String id = UUID.randomUUID().toString();
        return new UserEntity(id, id + "@example.com", "hash", name, role, UserStatus.ACTIVE, "2026-08", now, now);
    }

    private static String candidate(Fixture f) { return "Bearer " + f.candidateToken(); }
    private static String recruiter(Fixture f) { return "Bearer " + f.recruiterToken(); }
    private static String secondRecruiter(Fixture f) { return "Bearer " + f.recruiter2Token(); }

    private record Fixture(String recruiterToken, String candidateToken, String recruiter2Token,
                           String recruiterId, String candidateId, String email, String companyId, String resumeId) {}
}
