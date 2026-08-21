package com.adproject.conversation.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.adproject.conversation.domain.ConversationType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;

@Entity
@Table(name = "conversations")
public class ConversationEntity {
    @Id @Column(length = 36, columnDefinition = "char(36)") private String id;
    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_type", nullable = false, length = 32) private ConversationType conversationType;
    @Column(name = "application_id", unique = true, length = 36, columnDefinition = "char(36)") private String applicationId;
    @Column(name = "job_id", nullable = false, length = 36, columnDefinition = "char(36)") private String jobId;
    @Column(name = "candidate_id", nullable = false, length = 36, columnDefinition = "char(36)") private String candidateId;
    @Column(name = "company_id", nullable = false, length = 36, columnDefinition = "char(36)") private String companyId;
    @Column(name = "initiator_recruiter_id", length = 36, columnDefinition = "char(36)") private String initiatorRecruiterId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "last_message_at") private Instant lastMessageAt;

    protected ConversationEntity() {}

    public ConversationEntity(String id, String applicationId, String jobId, String candidateId, String companyId,
                              Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.conversationType = ConversationType.APPLICATION;
        this.applicationId = applicationId;
        this.jobId = jobId;
        this.candidateId = candidateId;
        this.companyId = companyId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ConversationEntity recruiterOutreach(String id, String jobId, String candidateId, String companyId,
                                                       String recruiterId, Instant now) {
        ConversationEntity conversation = new ConversationEntity();
        conversation.id = id;
        conversation.conversationType = ConversationType.RECRUITER_OUTREACH;
        conversation.jobId = jobId;
        conversation.candidateId = candidateId;
        conversation.companyId = companyId;
        conversation.initiatorRecruiterId = recruiterId;
        conversation.createdAt = now;
        conversation.updatedAt = now;
        return conversation;
    }

    /**
     * A candidate may contact the owner of a publicly visible job before applying.  The recruiter
     * id is intentionally retained so the existing conversation uniqueness key also makes this
     * operation idempotent for a Candidate/job/Recruiter combination.
     */
    public static ConversationEntity candidateInquiry(String id, String jobId, String candidateId, String companyId,
                                                      String recruiterId, Instant now) {
        ConversationEntity conversation = new ConversationEntity();
        conversation.id = id;
        conversation.conversationType = ConversationType.CANDIDATE_INQUIRY;
        conversation.jobId = jobId;
        conversation.candidateId = candidateId;
        conversation.companyId = companyId;
        conversation.initiatorRecruiterId = recruiterId;
        conversation.createdAt = now;
        conversation.updatedAt = now;
        return conversation;
    }

    public String getId() { return id; }
    public ConversationType getConversationType() { return conversationType; }
    public String getApplicationId() { return applicationId; }
    public String getJobId() { return jobId; }
    public String getCandidateId() { return candidateId; }
    public String getCompanyId() { return companyId; }
    public String getInitiatorRecruiterId() { return initiatorRecruiterId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastMessageAt() { return lastMessageAt; }

    public void touch(Instant now) {
        this.updatedAt = now;
        this.lastMessageAt = now;
    }
}
