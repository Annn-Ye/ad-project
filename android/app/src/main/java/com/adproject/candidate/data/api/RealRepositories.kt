package com.adproject.candidate.data.api

import com.adproject.candidate.core.auth.SessionManager
import com.adproject.candidate.data.contract.CandidateJob
import com.adproject.candidate.data.contract.CandidateJobApplicationState
import com.adproject.candidate.data.contract.CandidateJobDetail
import com.adproject.candidate.data.contract.CandidateRegisterRequest
import com.adproject.candidate.data.contract.CandidateOnboardingRequest
import com.adproject.candidate.data.contract.PasswordResetRequest
import com.adproject.candidate.data.contract.PasswordResetConfirmRequest
import com.adproject.candidate.data.contract.ConversationDetail
import com.adproject.candidate.data.contract.ConversationSummary
import com.adproject.candidate.data.contract.CursorMeta
import com.adproject.candidate.data.contract.EmploymentType
import com.adproject.candidate.data.contract.ErrorEnvelope
import com.adproject.candidate.data.contract.LoginRequest
import com.adproject.candidate.data.contract.Message
import com.adproject.candidate.data.contract.PageMeta
import com.adproject.candidate.data.contract.ReadStateRequest
import com.adproject.candidate.data.contract.RefreshTokenRequest
import com.adproject.candidate.data.contract.CandidateApplication
import com.adproject.candidate.data.contract.SendMessageRequest
import com.adproject.candidate.data.contract.SubmitApplicationRequest
import com.adproject.candidate.data.contract.ApplicationListFilter
import com.adproject.candidate.data.contract.AvatarMetadata
import com.adproject.candidate.data.contract.CandidateApplicationPage
import com.adproject.candidate.data.contract.WithdrawApplicationRequest
import com.adproject.candidate.data.contract.JobPreference
import com.adproject.candidate.data.contract.RecommendationEnvelope
import com.adproject.candidate.data.contract.SaveJobPreferenceRequest
import com.adproject.candidate.data.contract.CompanyPublicProfile
import com.adproject.candidate.data.contract.RecruiterPublicProfile
import com.adproject.candidate.data.contract.WorkplaceType
import com.squareup.moshi.Moshi
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
        val statusCode: Int? = null,
        val code: String? = null,
    ) : ApiResult<Nothing>
}

interface AuthRepository {
    suspend fun login(email: String, password: String): ApiResult<Unit>
    suspend fun register(fullName: String, email: String, password: String): ApiResult<Unit>
    suspend fun logout(): ApiResult<Unit>
    suspend fun requestPasswordReset(email: String): ApiResult<Unit> = ApiResult.Failure("Password reset is unavailable.")
    suspend fun confirmPasswordReset(email: String, code: String, newPassword: String): ApiResult<Unit> = ApiResult.Failure("Password reset is unavailable.")
    suspend fun completeOnboarding(request: CandidateOnboardingRequest): ApiResult<Unit> = ApiResult.Failure("Onboarding is unavailable.")
}

class RealAuthRepository(
    private val publicApi: AuthHttpApi,
    private val authenticatedApi: AuthHttpApi,
    private val sessionManager: SessionManager,
    moshi: Moshi,
) : AuthRepository {
    private val errors = ApiErrorParser(moshi)

    override suspend fun login(email: String, password: String): ApiResult<Unit> {
        val result = callAuth { publicApi.login(LoginRequest(email, password)) }
        return if (result is ApiResult.Failure && result.statusCode == 401) {
            result.copy(message = "Incorrect email or password.")
        } else result
    }

    override suspend fun register(fullName: String, email: String, password: String): ApiResult<Unit> = callAuth {
        publicApi.register(CandidateRegisterRequest(fullName = fullName, email = email, password = password,
            acceptedTermsVersion = "2026-08"))
    }

    override suspend fun logout(): ApiResult<Unit> {
        val refresh = sessionManager.tokens()?.refreshToken
        if (refresh == null) {
            sessionManager.clear()
            return ApiResult.Success(Unit)
        }
        return try {
            val response = authenticatedApi.logout(RefreshTokenRequest(refresh))
            sessionManager.clear()
            if (response.isSuccessful) ApiResult.Success(Unit) else errors.failure(response.code(), response.errorBody()?.string())
        } catch (_: Exception) {
            sessionManager.clear()
            ApiResult.Failure("Unable to sign out from the server. Your local session was cleared.")
        }
    }

    override suspend fun requestPasswordReset(email: String): ApiResult<Unit> = callUnit {
        publicApi.requestPasswordReset(PasswordResetRequest(email))
    }

    override suspend fun confirmPasswordReset(email: String, code: String, newPassword: String): ApiResult<Unit> = callUnit {
        publicApi.confirmPasswordReset(PasswordResetConfirmRequest(email, code, newPassword))
    }

    override suspend fun completeOnboarding(request: CandidateOnboardingRequest): ApiResult<Unit> {
        val result = callUnit { authenticatedApi.completeOnboarding(request) }
        if (result is ApiResult.Success) sessionManager.markOnboardingComplete()
        return result
    }

    private suspend fun callUnit(call: suspend () -> retrofit2.Response<Unit>): ApiResult<Unit> = try {
        val response = call()
        if (response.isSuccessful) ApiResult.Success(Unit)
        else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to connect. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Something went wrong. Please try again.")
    }

    private suspend fun callAuth(call: suspend () -> retrofit2.Response<com.adproject.candidate.data.contract.DataEnvelope<com.adproject.candidate.data.contract.AuthData>>): ApiResult<Unit> {
        return try {
            val response = call()
            val data = response.body()?.data
            if (!response.isSuccessful || data == null) return errors.failure(response.code(), response.errorBody()?.string())
            if (data.user.role.name != "CANDIDATE") return ApiResult.Failure("Please use a Candidate account.")
            sessionManager.save(data.accessToken, data.refreshToken, data.onboardingRequired ?: false)
            ApiResult.Success(Unit)
        } catch (_: IOException) {
            ApiResult.Failure("Unable to connect. Check your network and try again.")
        } catch (_: Exception) {
            ApiResult.Failure("Something went wrong. Please try again.")
        }
    }
}

data class CandidateJobPage(val jobs: List<CandidateJob>, val meta: PageMeta)

interface CandidateJobRepository {
    suspend fun jobs(q: String?, employmentType: EmploymentType?): ApiResult<CandidateJobPage>
    suspend fun job(jobId: String): ApiResult<CandidateJobDetail>
    suspend fun recommendations(
        q: String?,
        employmentType: EmploymentType?,
        workplaceType: WorkplaceType?,
        location: String?,
        minimumSalary: Long?,
        page: Int = 1,
        pageSize: Int = 10,
    ): ApiResult<RecommendationEnvelope>
    suspend fun saveJob(jobId: String): ApiResult<Unit>
    suspend fun unsaveJob(jobId: String): ApiResult<Unit>
    suspend fun savedJobs(page: Int = 1, pageSize: Int = 20): ApiResult<CandidateJobPage>
}

class RealCandidateJobRepository(
    private val api: CandidateJobHttpApi,
    moshi: Moshi,
) : CandidateJobRepository {
    private val errors = ApiErrorParser(moshi)

    override suspend fun jobs(q: String?, employmentType: EmploymentType?): ApiResult<CandidateJobPage> = try {
        val response = api.jobs(q?.trim()?.takeIf { it.isNotEmpty() }, employmentType)
        val body = response.body()
        if (!response.isSuccessful || body == null) errors.failure(response.code(), response.errorBody()?.string())
        else ApiResult.Success(CandidateJobPage(body.data.map(::toCandidateJob), body.meta))
    } catch (_: IOException) {
        ApiResult.Failure("Unable to load jobs. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to load jobs right now.")
    }

    override suspend fun job(jobId: String): ApiResult<CandidateJobDetail> = try {
        val response = api.job(jobId)
        val body = response.body()?.data
        if (!response.isSuccessful || body == null) errors.failure(response.code(), response.errorBody()?.string())
        else ApiResult.Success(CandidateJobDetail(
            job = toCandidateJob(body),
            matchAnalysis = body.matchAnalysis,
            applicationState = CandidateJobApplicationState.valueOf(body.applicationState ?: "NOT_APPLIED"),
            isSaved = body.isSaved ?: false,
        ))
    } catch (_: IOException) {
        ApiResult.Failure("Unable to load this job. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to load this job right now.")
    }

    override suspend fun recommendations(
        q: String?,
        employmentType: EmploymentType?,
        workplaceType: WorkplaceType?,
        location: String?,
        minimumSalary: Long?,
        page: Int,
        pageSize: Int,
    ): ApiResult<RecommendationEnvelope> = try {
        val response = api.recommendations(q?.trim()?.takeIf { it.isNotEmpty() }, employmentType,
            workplaceType, location?.trim()?.takeIf { it.isNotEmpty() }, minimumSalary, page, pageSize)
        val body = response.body()
        if (response.isSuccessful && body != null) ApiResult.Success(body)
        else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to load recommendations. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to load recommendations right now.")
    }

    override suspend fun saveJob(jobId: String): ApiResult<Unit> = try {
        val response = api.saveJob(jobId)
        if (response.isSuccessful) ApiResult.Success(Unit)
        else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to save this job. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to save this job right now.")
    }

    override suspend fun unsaveJob(jobId: String): ApiResult<Unit> = try {
        val response = api.unsaveJob(jobId)
        if (response.isSuccessful) ApiResult.Success(Unit)
        else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to remove this job. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to remove this job right now.")
    }

    override suspend fun savedJobs(page: Int, pageSize: Int): ApiResult<CandidateJobPage> = try {
        val response = api.savedJobs(page, pageSize)
        val body = response.body()
        if (response.isSuccessful && body != null) ApiResult.Success(
            CandidateJobPage(body.data.map(::toCandidateJob), body.meta))
        else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to load your saved jobs. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to load your saved jobs right now.")
    }

    private fun toCandidateJob(job: NetworkCandidateJob) = CandidateJob(
        job.jobId, job.title, job.company, job.employmentType, job.workplaceType, job.location, job.salary,
        job.description, job.requirements, job.skills, job.deadline, job.visibility, job.status, job.publishedAt,
        job.version, job.createdAt, job.updatedAt, job.matchScore, job.recruiter, job.isSaved,
    )
}

interface CandidateRecommendationRepository {
    suspend fun preferences(): ApiResult<JobPreference>
    suspend fun savePreferences(request: SaveJobPreferenceRequest): ApiResult<JobPreference>
}

class RealCandidateRecommendationRepository(
    private val api: CandidateRecommendationHttpApi,
    moshi: Moshi,
) : CandidateRecommendationRepository {
    private val errors = ApiErrorParser(moshi)

    override suspend fun preferences(): ApiResult<JobPreference> = call { api.preferences() }

    override suspend fun savePreferences(request: SaveJobPreferenceRequest): ApiResult<JobPreference> =
        call { api.savePreferences(request) }

    private suspend fun call(
        block: suspend () -> retrofit2.Response<com.adproject.candidate.data.contract.DataEnvelope<JobPreference>>,
    ): ApiResult<JobPreference> = try {
        val response = block()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) ApiResult.Success(data)
        else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to load job preferences. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to load job preferences right now.")
    }
}

class ApiErrorParser(moshi: Moshi) {
    private val adapter = moshi.adapter(ErrorEnvelope::class.java)

    fun failure(statusCode: Int, body: String?): ApiResult.Failure {
        val error = body?.let { runCatching { adapter.fromJson(it)?.error }.getOrNull() }
        val safe = when (statusCode) {
            401 -> "Your session has expired. Please sign in again."
            403 -> "You do not have permission to perform this action."
            404 -> "This job is no longer available."
            in 500..599 -> "The service is temporarily unavailable. Please try again."
            else -> error?.message?.takeIf { it.isNotBlank() } ?: "Request failed. Please try again."
        }
        return ApiResult.Failure(safe, error?.fieldErrors.orEmpty(), statusCode, error?.code)
    }
}

interface CandidateProfileRepository {
    suspend fun get(): ApiResult<com.adproject.candidate.data.contract.CandidateProfileDto>
    suspend fun update(request: com.adproject.candidate.data.contract.UpdateProfileRequest): ApiResult<com.adproject.candidate.data.contract.CandidateProfileDto>
}
class RealCandidateProfileRepository(private val api: CandidateProfileHttpApi, moshi: Moshi) : CandidateProfileRepository {
    private val errors=ApiErrorParser(moshi)
    override suspend fun get()=call { api.get() }
    override suspend fun update(request: com.adproject.candidate.data.contract.UpdateProfileRequest)=call { api.update(request) }
    private suspend fun call(block:suspend()->retrofit2.Response<com.adproject.candidate.data.contract.DataEnvelope<com.adproject.candidate.data.contract.CandidateProfileDto>>):ApiResult<com.adproject.candidate.data.contract.CandidateProfileDto> = try { val r=block(); val d=r.body()?.data; if(r.isSuccessful&&d!=null) ApiResult.Success(d) else errors.failure(r.code(),r.errorBody()?.string()) } catch(_:IOException){ApiResult.Failure("Unable to load your profile. Check your network and try again.")} catch(_:Exception){ApiResult.Failure("Unable to load your profile right now.")}
}
data class AvatarUpload(val fileName: String, val contentType: String, val bytes: ByteArray)

interface CandidateAvatarRepository {
    suspend fun upload(request: AvatarUpload): ApiResult<AvatarMetadata>
    suspend fun delete(): ApiResult<Unit>
}

class RealCandidateAvatarRepository(private val api: CandidateProfileHttpApi, moshi: Moshi) : CandidateAvatarRepository {
    private val errors = ApiErrorParser(moshi)
    override suspend fun upload(request: AvatarUpload): ApiResult<AvatarMetadata> = try {
        val mediaType = request.contentType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
        val filePart = MultipartBody.Part.createFormData("file", request.fileName, request.bytes.toRequestBody(mediaType))
        val response = api.uploadAvatar(filePart)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) ApiResult.Success(data)
        else {
            val failure = errors.failure(response.code(), response.errorBody()?.string())
            when (failure.code) {
                "FILE_TOO_LARGE" -> failure.copy(message = "This image is larger than 5 MB.")
                "VALIDATION_ERROR" -> failure.copy(message = "Only PNG or JPEG images are supported.")
                else -> failure
            }
        }
    } catch (_: IOException) {
        ApiResult.Failure("Unable to upload your avatar. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to upload your avatar right now.")
    }

    override suspend fun delete(): ApiResult<Unit> = try {
        val response = api.deleteAvatar()
        if (response.isSuccessful) ApiResult.Success(Unit)
        else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to remove your avatar. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to remove your avatar right now.")
    }
}

interface CandidateResumeRepository {
    suspend fun get(): ApiResult<com.adproject.candidate.data.contract.Resume>
    suspend fun save(request: com.adproject.candidate.data.contract.SaveResumeRequest): ApiResult<com.adproject.candidate.data.contract.Resume>
}
class RealCandidateResumeRepository(private val api: CandidateResumeHttpApi, moshi: Moshi) : CandidateResumeRepository {
    private val errors=ApiErrorParser(moshi)
    override suspend fun get()=call { api.get() }
    override suspend fun save(request: com.adproject.candidate.data.contract.SaveResumeRequest)=call { api.save(request) }
    private suspend fun call(block:suspend()->retrofit2.Response<com.adproject.candidate.data.contract.DataEnvelope<com.adproject.candidate.data.contract.Resume>>):ApiResult<com.adproject.candidate.data.contract.Resume> = try { val r=block(); val d=r.body()?.data; if(r.isSuccessful&&d!=null) ApiResult.Success(d) else if(r.code()==404) ApiResult.Failure("No resume has been created yet.",statusCode=404) else errors.failure(r.code(),r.errorBody()?.string()) } catch(_:IOException){ApiResult.Failure("Unable to load your resume. Check your network and try again.")} catch(_:Exception){ApiResult.Failure("Unable to load your resume right now.")}
}

interface CandidateAgentRepository {
    suspend fun create(request: com.adproject.candidate.data.contract.CreateAgentRunRequest): ApiResult<com.adproject.candidate.data.contract.AgentRun>
    suspend fun get(runId: String): ApiResult<com.adproject.candidate.data.contract.AgentRun>
    suspend fun confirm(runId: String, idempotencyKey: String,
                        request: com.adproject.candidate.data.contract.ConfirmAgentRunRequest): ApiResult<com.adproject.candidate.data.contract.AgentRun>
    suspend fun cancel(runId: String): ApiResult<com.adproject.candidate.data.contract.AgentRun>
    suspend fun conversations(): ApiResult<List<com.adproject.candidate.data.contract.AgentConversationSummary>>
    suspend fun recentConversation(): ApiResult<com.adproject.candidate.data.contract.AgentConversation>
    suspend fun conversation(conversationId: String): ApiResult<com.adproject.candidate.data.contract.AgentConversation>
    suspend fun deleteConversation(conversationId: String): ApiResult<Unit>
}

class RealCandidateAgentRepository(private val api: CandidateAgentHttpApi, moshi: Moshi) : CandidateAgentRepository {
    private val errors = ApiErrorParser(moshi)
    override suspend fun create(request: com.adproject.candidate.data.contract.CreateAgentRunRequest) =
        call { api.create(request) }
    override suspend fun get(runId: String) = call { api.get(runId) }
    override suspend fun confirm(runId: String, idempotencyKey: String,
                                 request: com.adproject.candidate.data.contract.ConfirmAgentRunRequest) =
        call { api.confirm(runId, idempotencyKey, request) }
    override suspend fun cancel(runId: String) = call { api.cancel(runId) }

    override suspend fun conversations(): ApiResult<List<com.adproject.candidate.data.contract.AgentConversationSummary>> = try {
        val response = api.conversations()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) ApiResult.Success(data)
        else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to load Agent conversation history.")
    } catch (_: Exception) {
        ApiResult.Failure("The Agent conversation list could not be loaded.")
    }

    override suspend fun recentConversation() = conversationCall { api.recentConversation() }

    override suspend fun conversation(conversationId: String) = conversationCall { api.conversation(conversationId) }

    override suspend fun deleteConversation(conversationId: String): ApiResult<Unit> = try {
        val response = api.deleteConversation(conversationId)
        if (response.isSuccessful) ApiResult.Success(Unit)
        else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to reach the Agent service. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("The Agent request could not be completed.")
    }

    private suspend fun call(block: suspend () -> retrofit2.Response<com.adproject.candidate.data.contract.DataEnvelope<com.adproject.candidate.data.contract.AgentRun>>): ApiResult<com.adproject.candidate.data.contract.AgentRun> = try {
        val response = block()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) ApiResult.Success(data)
        else {
            val failure = errors.failure(response.code(), response.errorBody()?.string())
            when {
                response.code() == 404 -> failure.copy(message = "This Agent run is no longer available.")
                response.code() == 409 && failure.code == null ->
                    failure.copy(message = "This Agent preview is no longer executable. Create a new request.")
                else -> failure
            }
        }
    } catch (_: IOException) {
        ApiResult.Failure("Unable to reach the Agent service. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("The Agent request could not be completed.")
    }

    private suspend fun conversationCall(block: suspend () -> retrofit2.Response<com.adproject.candidate.data.contract.DataEnvelope<com.adproject.candidate.data.contract.AgentConversation>>): ApiResult<com.adproject.candidate.data.contract.AgentConversation> = try {
        val response = block()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) ApiResult.Success(data)
        else errors.failure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to load Agent conversation history.")
    } catch (_: Exception) {
        ApiResult.Failure("The Agent conversation could not be loaded.")
    }
}

interface CandidateApplicationRepository {
    suspend fun applications(filter: ApplicationListFilter?, page: Int, pageSize: Int = 20): ApiResult<CandidateApplicationPage>
    suspend fun application(applicationId: String): ApiResult<CandidateApplication>
    suspend fun withdraw(applicationId: String, request: WithdrawApplicationRequest): ApiResult<CandidateApplication>
    suspend fun submit(jobId: String, idempotencyKey: String,
                       request: SubmitApplicationRequest): ApiResult<CandidateApplication>
}

class RealCandidateApplicationRepository(
    private val api: CandidateApplicationHttpApi,
    moshi: Moshi,
) : CandidateApplicationRepository {
    private val errors = ApiErrorParser(moshi)

    override suspend fun applications(filter: ApplicationListFilter?, page: Int,
                                      pageSize: Int): ApiResult<CandidateApplicationPage> = try {
        val response = api.applications(filter, page, pageSize)
        val body = response.body()
        if (response.isSuccessful && body != null) {
            ApiResult.Success(CandidateApplicationPage(body.data, body.meta))
        } else applicationFailure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to load your applications. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to load your applications right now.")
    }

    override suspend fun application(applicationId: String): ApiResult<CandidateApplication> = try {
        val response = api.application(applicationId)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) ApiResult.Success(data)
        else applicationFailure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to load this application. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to load this application right now.")
    }

    override suspend fun withdraw(applicationId: String,
                                  request: WithdrawApplicationRequest): ApiResult<CandidateApplication> = try {
        val response = api.withdraw(applicationId, request)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) ApiResult.Success(data)
        else {
            val failure = applicationFailure(response.code(), response.errorBody()?.string())
            when (failure.code) {
                "VERSION_CONFLICT" -> failure.copy(message = "This application changed. Refresh before trying again.")
                "INVALID_APPLICATION_TRANSITION" -> failure.copy(message = "This application can no longer be withdrawn.")
                else -> failure
            }
        }
    } catch (_: IOException) {
        ApiResult.Failure("Unable to withdraw this application. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to withdraw this application right now.")
    }

    override suspend fun submit(jobId: String, idempotencyKey: String,
                                request: SubmitApplicationRequest): ApiResult<CandidateApplication> = try {
        val response = api.submit(jobId, idempotencyKey, request)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) ApiResult.Success(data)
        else {
            val failure = errors.failure(response.code(), response.errorBody()?.string())
            when (failure.code) {
                "APPLICATION_ALREADY_EXISTS" -> failure.copy(message = "You have already applied for this job.")
                "IDEMPOTENCY_KEY_REUSED" -> failure.copy(message = "This submission could not be retried safely. Please return to the job and try again.")
                else -> failure
            }
        }
    } catch (_: IOException) {
        ApiResult.Failure("Unable to submit your application. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to submit your application right now.")
    }

    private fun applicationFailure(statusCode: Int, body: String?): ApiResult.Failure {
        val failure = errors.failure(statusCode, body)
        return if (statusCode == 404) failure.copy(message = "This application is no longer available.") else failure
    }
}

data class ConversationListResult(val conversations: List<ConversationSummary>, val meta: PageMeta)
data class MessageListResult(val messages: List<Message>, val meta: CursorMeta)

data class AttachmentUpload(
    val clientMessageId: String,
    val body: String?,
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
)

data class DownloadedAttachment(val bytes: ByteArray, val contentType: String)

interface CandidateConversationRepository {
    suspend fun conversations(): ApiResult<ConversationListResult>
    suspend fun conversation(conversationId: String): ApiResult<ConversationDetail>
    suspend fun startInquiry(jobId: String): ApiResult<ConversationDetail> =
        ApiResult.Failure("Messaging is unavailable right now.")
    suspend fun messages(conversationId: String, before: String? = null): ApiResult<MessageListResult>
    suspend fun sendMessage(conversationId: String, idempotencyKey: String, request: SendMessageRequest): ApiResult<Message>
    suspend fun sendMessageWithAttachment(conversationId: String, idempotencyKey: String, request: AttachmentUpload): ApiResult<Message>
    suspend fun downloadAttachment(conversationId: String, messageId: String): ApiResult<DownloadedAttachment>
    suspend fun markRead(conversationId: String, request: ReadStateRequest): ApiResult<Unit>
}

class RealCandidateConversationRepository(
    private val api: CandidateConversationHttpApi,
    moshi: Moshi,
) : CandidateConversationRepository {
    private val errors = ApiErrorParser(moshi)

    override suspend fun conversations(): ApiResult<ConversationListResult> = try {
        val response = api.conversations()
        val body = response.body()
        if (response.isSuccessful && body != null) ApiResult.Success(ConversationListResult(body.data, body.meta))
        else conversationFailure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to load conversations. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to load conversations right now.")
    }

    override suspend fun conversation(conversationId: String): ApiResult<ConversationDetail> = try {
        val response = api.conversation(conversationId)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) ApiResult.Success(data)
        else conversationFailure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to load this conversation. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to load this conversation right now.")
    }

    override suspend fun startInquiry(jobId: String): ApiResult<ConversationDetail> = try {
        val response = api.startInquiry(jobId)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) ApiResult.Success(data)
        else conversationFailure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to start this conversation. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to start this conversation right now.")
    }

    override suspend fun messages(conversationId: String, before: String?): ApiResult<MessageListResult> = try {
        val response = api.messages(conversationId, before)
        val body = response.body()
        if (response.isSuccessful && body != null) ApiResult.Success(MessageListResult(body.data, body.meta))
        else conversationFailure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to load messages. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to load messages right now.")
    }

    override suspend fun sendMessage(conversationId: String, idempotencyKey: String,
                                    request: SendMessageRequest): ApiResult<Message> = try {
        val response = api.sendMessage(conversationId, idempotencyKey, request)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) ApiResult.Success(data)
        else {
            val failure = errors.failure(response.code(), response.errorBody()?.string())
            when (failure.code) {
                "IDEMPOTENCY_KEY_REUSED" -> failure.copy(message = "This message could not be retried safely. Please send a new message.")
                "CONVERSATION_CLOSED" -> failure.copy(message = "This conversation is read-only.")
                else -> failure
            }
        }
    } catch (_: IOException) {
        ApiResult.Failure("Unable to send your message. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to send your message right now.")
    }

    override suspend fun sendMessageWithAttachment(conversationId: String, idempotencyKey: String,
                                                  request: AttachmentUpload): ApiResult<Message> = try {
        val mediaType = request.contentType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
        val filePart = MultipartBody.Part.createFormData("file", request.fileName, request.bytes.toRequestBody(mediaType))
        val clientIdBody = request.clientMessageId.toRequestBody("text/plain".toMediaType())
        val bodyPart = request.body?.toRequestBody("text/plain".toMediaType())
        val response = api.sendMessageWithAttachment(conversationId, idempotencyKey, clientIdBody, bodyPart, filePart)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) ApiResult.Success(data)
        else {
            val failure = errors.failure(response.code(), response.errorBody()?.string())
            when (failure.code) {
                "IDEMPOTENCY_KEY_REUSED" -> failure.copy(message = "This message could not be retried safely. Please send a new message.")
                "CONVERSATION_CLOSED" -> failure.copy(message = "This conversation is read-only.")
                "FILE_TOO_LARGE" -> failure.copy(message = "This file is larger than 10 MB.")
                "VALIDATION_ERROR" -> failure.copy(message = "Only PDF, DOC, DOCX, TXT, PNG, JPG or JPEG files are supported.")
                else -> failure
            }
        }
    } catch (_: IOException) {
        ApiResult.Failure("Unable to send your attachment. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to send your attachment right now.")
    }

    override suspend fun downloadAttachment(conversationId: String, messageId: String): ApiResult<DownloadedAttachment> = try {
        val response = api.downloadAttachment(conversationId, messageId)
        val body = response.body()
        if (response.isSuccessful && body != null) {
            ApiResult.Success(DownloadedAttachment(body.bytes(), body.contentType()?.toString() ?: "application/octet-stream"))
        } else conversationFailure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to download this attachment. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to download this attachment right now.")
    }

    override suspend fun markRead(conversationId: String, request: ReadStateRequest): ApiResult<Unit> = try {
        val response = api.markRead(conversationId, request)
        if (response.isSuccessful) ApiResult.Success(Unit)
        else conversationFailure(response.code(), response.errorBody()?.string())
    } catch (_: IOException) {
        ApiResult.Failure("Unable to update this conversation. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to update this conversation right now.")
    }

    private fun conversationFailure(statusCode: Int, body: String?): ApiResult.Failure {
        val failure = errors.failure(statusCode, body)
        return if (statusCode == 404) failure.copy(message = "This conversation is no longer available.") else failure
    }
}

interface CandidatePublicProfileRepository {
    suspend fun recruiter(recruiterId: String): ApiResult<RecruiterPublicProfile>
    suspend fun company(companyId: String): ApiResult<CompanyPublicProfile>
}

class RealCandidatePublicProfileRepository(
    private val api: CandidatePublicProfileHttpApi,
    moshi: Moshi,
) : CandidatePublicProfileRepository {
    private val errors = ApiErrorParser(moshi)

    override suspend fun recruiter(recruiterId: String): ApiResult<RecruiterPublicProfile> = try {
        val response = api.recruiter(recruiterId)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) ApiResult.Success(data)
        else profileFailure(response.code(), response.errorBody()?.string(), "recruiter")
    } catch (_: IOException) {
        ApiResult.Failure("Unable to load this recruiter. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to load this recruiter right now.")
    }

    override suspend fun company(companyId: String): ApiResult<CompanyPublicProfile> = try {
        val response = api.company(companyId)
        val data = response.body()?.data
        if (response.isSuccessful && data != null) ApiResult.Success(data)
        else profileFailure(response.code(), response.errorBody()?.string(), "company")
    } catch (_: IOException) {
        ApiResult.Failure("Unable to load this company. Check your network and try again.")
    } catch (_: Exception) {
        ApiResult.Failure("Unable to load this company right now.")
    }

    private fun profileFailure(statusCode: Int, body: String?, kind: String): ApiResult.Failure {
        val failure = errors.failure(statusCode, body)
        return if (statusCode == 404) failure.copy(message = "This $kind is no longer available.") else failure
    }
}
