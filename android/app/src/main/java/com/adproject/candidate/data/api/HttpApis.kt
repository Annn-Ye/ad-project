package com.adproject.candidate.data.api

import com.adproject.candidate.data.contract.AuthData
import com.adproject.candidate.data.contract.CandidateRegisterRequest
import com.adproject.candidate.data.contract.CandidateOnboardingRequest
import com.adproject.candidate.data.contract.PasswordResetRequest
import com.adproject.candidate.data.contract.PasswordResetConfirmRequest
import com.adproject.candidate.data.contract.Company
import com.adproject.candidate.data.contract.ConversationDetail
import com.adproject.candidate.data.contract.ConversationSummary
import com.adproject.candidate.data.contract.CursorMeta
import com.adproject.candidate.data.contract.DataEnvelope
import com.adproject.candidate.data.contract.EmploymentType
import com.adproject.candidate.data.contract.ListEnvelope
import com.adproject.candidate.data.contract.LoginRequest
import com.adproject.candidate.data.contract.MatchAnalysis
import com.adproject.candidate.data.contract.Message
import com.adproject.candidate.data.contract.PageMeta
import com.adproject.candidate.data.contract.ReadStateRequest
import com.adproject.candidate.data.contract.RecruiterContact
import com.adproject.candidate.data.contract.CompanyPublicProfile
import com.adproject.candidate.data.contract.RecruiterPublicProfile
import com.adproject.candidate.data.contract.RefreshTokenRequest
import com.adproject.candidate.data.contract.Salary
import com.adproject.candidate.data.contract.SendMessageRequest
import com.adproject.candidate.data.contract.TokenData
import com.adproject.candidate.data.contract.Visibility
import com.adproject.candidate.data.contract.WorkplaceType
import com.adproject.candidate.data.contract.JobStatus
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Header

interface AuthHttpApi {
    @POST("auth/register") suspend fun register(@Body request: CandidateRegisterRequest): Response<DataEnvelope<AuthData>>
    @POST("auth/login") suspend fun login(@Body request: LoginRequest): Response<DataEnvelope<AuthData>>
    @POST("auth/refresh") suspend fun refresh(@Body request: RefreshTokenRequest): Response<DataEnvelope<TokenData>>
    @POST("auth/logout") suspend fun logout(@Body request: RefreshTokenRequest): Response<Unit>
    @POST("auth/password-reset/request") suspend fun requestPasswordReset(@Body request: PasswordResetRequest): Response<Unit>
    @POST("auth/password-reset/confirm") suspend fun confirmPasswordReset(@Body request: PasswordResetConfirmRequest): Response<Unit>
    @POST("candidate/onboarding") suspend fun completeOnboarding(@Body request: CandidateOnboardingRequest): Response<Unit>
}

interface CandidateJobHttpApi {
    @GET("jobs")
    suspend fun jobs(
        @Query("q") q: String?,
        @Query("employmentType") employmentType: EmploymentType?,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): Response<ListEnvelope<NetworkCandidateJob, PageMeta>>

    @GET("jobs/{jobId}")
    suspend fun job(@Path("jobId") jobId: String): Response<DataEnvelope<NetworkCandidateJob>>

    @GET("candidate/recommendations/jobs")
    suspend fun recommendations(
        @Query("q") q: String?,
        @Query("employmentType") employmentType: EmploymentType?,
        @Query("workplaceType") workplaceType: WorkplaceType?,
        @Query("location") location: String?,
        @Query("minimumSalary") minimumSalary: Long?,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 10,
    ): Response<com.adproject.candidate.data.contract.RecommendationEnvelope>

    @GET("candidate/saved-jobs")
    suspend fun savedJobs(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): Response<ListEnvelope<NetworkCandidateJob, PageMeta>>

    @PUT("candidate/saved-jobs/{jobId}")
    suspend fun saveJob(@Path("jobId") jobId: String): Response<Unit>

    @DELETE("candidate/saved-jobs/{jobId}")
    suspend fun unsaveJob(@Path("jobId") jobId: String): Response<Unit>
}

interface CandidateRecommendationHttpApi {
    @GET("candidate/job-preferences")
    suspend fun preferences(): Response<DataEnvelope<com.adproject.candidate.data.contract.JobPreference>>

    @PUT("candidate/job-preferences")
    suspend fun savePreferences(
        @Body request: com.adproject.candidate.data.contract.SaveJobPreferenceRequest,
    ): Response<DataEnvelope<com.adproject.candidate.data.contract.JobPreference>>
}

interface CandidateProfileHttpApi {
    @GET("candidate/profile") suspend fun get(): Response<DataEnvelope<com.adproject.candidate.data.contract.CandidateProfileDto>>
    @PATCH("candidate/profile") suspend fun update(@Body request: com.adproject.candidate.data.contract.UpdateProfileRequest): Response<DataEnvelope<com.adproject.candidate.data.contract.CandidateProfileDto>>

    @Multipart
    @POST("profile/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): Response<DataEnvelope<com.adproject.candidate.data.contract.AvatarMetadata>>

    @DELETE("profile/avatar")
    suspend fun deleteAvatar(): Response<Unit>
}

interface CandidateResumeHttpApi {
    @GET("candidate/resume") suspend fun get(): Response<DataEnvelope<com.adproject.candidate.data.contract.Resume>>
    @PUT("candidate/resume") suspend fun save(@Body request: com.adproject.candidate.data.contract.SaveResumeRequest): Response<DataEnvelope<com.adproject.candidate.data.contract.Resume>>
}

interface CandidateAgentHttpApi {
    @POST("agent/runs")
    suspend fun create(
        @Body request: com.adproject.candidate.data.contract.CreateAgentRunRequest,
    ): Response<DataEnvelope<com.adproject.candidate.data.contract.AgentRun>>

    @GET("agent/conversations")
    suspend fun conversations(): Response<DataEnvelope<List<com.adproject.candidate.data.contract.AgentConversationSummary>>>

    @GET("agent/conversations/recent")
    suspend fun recentConversation(): Response<DataEnvelope<com.adproject.candidate.data.contract.AgentConversation>>

    @GET("agent/conversations/{conversationId}")
    suspend fun conversation(
        @Path("conversationId") conversationId: String,
    ): Response<DataEnvelope<com.adproject.candidate.data.contract.AgentConversation>>

    @GET("agent/runs/{runId}")
    suspend fun get(
        @Path("runId") runId: String,
    ): Response<DataEnvelope<com.adproject.candidate.data.contract.AgentRun>>

    @POST("agent/runs/{runId}/confirm")
    suspend fun confirm(
        @Path("runId") runId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: com.adproject.candidate.data.contract.ConfirmAgentRunRequest,
    ): Response<DataEnvelope<com.adproject.candidate.data.contract.AgentRun>>

    @POST("agent/runs/{runId}/cancel")
    suspend fun cancel(
        @Path("runId") runId: String,
    ): Response<DataEnvelope<com.adproject.candidate.data.contract.AgentRun>>

    @DELETE("agent/conversations/{conversationId}")
    suspend fun deleteConversation(
        @Path("conversationId") conversationId: String,
    ): Response<Unit>
}

interface CandidateApplicationHttpApi {
    @GET("candidate/applications")
    suspend fun applications(
        @Query("filter") filter: com.adproject.candidate.data.contract.ApplicationListFilter?,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): Response<ListEnvelope<com.adproject.candidate.data.contract.CandidateApplicationSummary,
            com.adproject.candidate.data.contract.CandidateApplicationListMeta>>

    @GET("candidate/applications/{applicationId}")
    suspend fun application(
        @Path("applicationId") applicationId: String,
    ): Response<DataEnvelope<com.adproject.candidate.data.contract.CandidateApplication>>

    @POST("candidate/applications/{applicationId}/withdraw")
    suspend fun withdraw(
        @Path("applicationId") applicationId: String,
        @Body request: com.adproject.candidate.data.contract.WithdrawApplicationRequest,
    ): Response<DataEnvelope<com.adproject.candidate.data.contract.CandidateApplication>>

    @POST("jobs/{jobId}/applications")
    suspend fun submit(
        @Path("jobId") jobId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: com.adproject.candidate.data.contract.SubmitApplicationRequest,
    ): Response<DataEnvelope<com.adproject.candidate.data.contract.CandidateApplication>>
}

interface CandidateConversationHttpApi {
    @GET("candidate/conversations")
    suspend fun conversations(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): Response<ListEnvelope<ConversationSummary, PageMeta>>

    @GET("candidate/conversations/{conversationId}")
    suspend fun conversation(@Path("conversationId") conversationId: String): Response<DataEnvelope<ConversationDetail>>

    @POST("candidate/conversations/job/{jobId}")
    suspend fun startInquiry(@Path("jobId") jobId: String): Response<DataEnvelope<ConversationDetail>>

    @GET("candidate/conversations/{conversationId}/messages")
    suspend fun messages(
        @Path("conversationId") conversationId: String,
        @Query("before") before: String?,
        @Query("limit") limit: Int = 30,
    ): Response<ListEnvelope<Message, CursorMeta>>

    @POST("candidate/conversations/{conversationId}/messages")
    suspend fun sendMessage(
        @Path("conversationId") conversationId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: SendMessageRequest,
    ): Response<DataEnvelope<Message>>

    @Multipart
    @POST("candidate/conversations/{conversationId}/messages/attachment")
    suspend fun sendMessageWithAttachment(
        @Path("conversationId") conversationId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Part("clientMessageId") clientMessageId: RequestBody,
        @Part("body") body: RequestBody?,
        @Part file: MultipartBody.Part,
    ): Response<DataEnvelope<Message>>

    @GET("candidate/conversations/{conversationId}/messages/{messageId}/attachment")
    suspend fun downloadAttachment(
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: String,
    ): Response<ResponseBody>

    @PUT("candidate/conversations/{conversationId}/read-state")
    suspend fun markRead(
        @Path("conversationId") conversationId: String,
        @Body request: ReadStateRequest,
    ): Response<Unit>
}

interface CandidatePublicProfileHttpApi {
    @GET("candidate/recruiters/{recruiterId}")
    suspend fun recruiter(@Path("recruiterId") recruiterId: String): Response<DataEnvelope<RecruiterPublicProfile>>

    @GET("candidate/companies/{companyId}")
    suspend fun company(@Path("companyId") companyId: String): Response<DataEnvelope<CompanyPublicProfile>>
}

data class NetworkCandidateJob(
    val jobId: String,
    val title: String,
    val company: Company,
    val employmentType: EmploymentType,
    val workplaceType: WorkplaceType,
    val location: String,
    val salary: Salary,
    val description: String,
    val requirements: List<String>,
    val skills: List<String>,
    val deadline: String?,
    val visibility: Visibility,
    val status: JobStatus,
    val publishedAt: String?,
    val version: Int,
    val createdAt: String,
    val updatedAt: String,
    val matchScore: Int?,
    val recruiter: RecruiterContact?,
    val matchAnalysis: MatchAnalysis? = null,
    val applicationState: String? = null,
    val isSaved: Boolean? = null,
)
