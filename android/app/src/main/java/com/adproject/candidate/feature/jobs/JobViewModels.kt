package com.adproject.candidate.feature.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adproject.candidate.data.api.ApiResult
import com.adproject.candidate.data.api.CandidateJobRepository
import com.adproject.candidate.data.contract.CandidateJob
import com.adproject.candidate.data.contract.EmploymentType
import com.adproject.candidate.data.contract.MatchAnalysis
import com.adproject.candidate.data.contract.RecommendedJob
import com.adproject.candidate.data.contract.WorkplaceType
import com.adproject.candidate.data.api.CandidateConversationRepository
import com.adproject.candidate.data.model.Job
import com.adproject.candidate.data.model.JobDetailData
import com.adproject.candidate.data.model.JobFeedData
import com.adproject.candidate.data.model.RecruiterContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JobFeedUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val data: JobFeedData? = null,
    val message: String? = null,
    val query: String = "",
    val employmentType: EmploymentType? = null,
    val workplaceType: WorkplaceType? = null,
    val location: String? = null,
    val minimumSalary: Long? = null,
    val page: Int = 1,
    val hasNext: Boolean = false,
    val total: Int = 0,
    val loadingMore: Boolean = false,
    val loadMoreError: Boolean = false,
    val savingJobs: Set<String> = emptySet(),
    val saveError: String? = null,
)

class JobFeedViewModel(private val repository: CandidateJobRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(JobFeedUiState())
    val state: StateFlow<JobFeedUiState> = mutableState.asStateFlow()

    private var generation = 0

    init { load() }

    fun updateQuery(value: String) = mutableState.update { it.copy(query = value) }

    fun selectEmploymentType(value: EmploymentType?) {
        mutableState.update { it.copy(employmentType = value) }
        load()
    }

    fun selectWorkplaceType(value: WorkplaceType?) {
        mutableState.update { it.copy(workplaceType = value) }
        load()
    }

    fun selectLocation(value: String?) {
        mutableState.update { it.copy(location = value) }
        load()
    }

    fun selectMinimumSalary(value: Long?) {
        mutableState.update { it.copy(minimumSalary = value) }
        load()
    }

    fun applyFilters(employmentType: EmploymentType?, workplaceType: WorkplaceType?,
                     location: String?, minimumSalary: Long?) {
        mutableState.update {
            it.copy(
                employmentType = employmentType,
                workplaceType = workplaceType,
                location = location?.trim()?.takeIf(String::isNotEmpty),
                minimumSalary = minimumSalary,
            )
        }
        load()
    }

    fun clearFilters() {
        mutableState.update {
            it.copy(employmentType = null, workplaceType = null, location = null, minimumSalary = null)
        }
        load()
    }

    fun toggleSave(jobId: String) {
        val snapshot = mutableState.value
        val job = snapshot.data?.jobs?.firstOrNull { it.jobId == jobId } ?: return
        if (jobId in snapshot.savingJobs) return
        val targetSaved = !job.isSaved
        mutableState.update { state ->
            state.copy(
                data = state.data?.copy(jobs = state.data.jobs.map {
                    if (it.jobId == jobId) it.copy(isSaved = targetSaved) else it
                }),
                savingJobs = state.savingJobs + jobId,
                saveError = null,
            )
        }
        viewModelScope.launch {
            val result = if (targetSaved) repository.saveJob(jobId) else repository.unsaveJob(jobId)
            mutableState.update { state ->
                when (result) {
                    is ApiResult.Success -> state.copy(savingJobs = state.savingJobs - jobId)
                    is ApiResult.Failure -> state.copy(
                        data = state.data?.copy(jobs = state.data.jobs.map {
                            if (it.jobId == jobId) it.copy(isSaved = !targetSaved) else it
                        }),
                        savingJobs = state.savingJobs - jobId,
                        saveError = result.message,
                    )
                }
            }
        }
    }

    fun search() = load()
    fun retry() = load()
    fun refresh() = load(refreshing = true)

    fun loadMore() {
        val snapshot = mutableState.value
        if (snapshot.loading || snapshot.refreshing || snapshot.loadingMore || snapshot.loadMoreError) return
        if (!snapshot.hasNext || snapshot.data == null) return
        loadPage(snapshot.page + 1, append = true)
    }

    fun retryLoadMore() {
        val snapshot = mutableState.value
        if (snapshot.loading || snapshot.refreshing || snapshot.loadingMore) return
        if (!snapshot.hasNext || snapshot.data == null) return
        loadPage(snapshot.page + 1, append = true)
    }

    private fun load(refreshing: Boolean = false) = loadPage(page = 1, append = false, refreshing = refreshing)

    private fun loadPage(page: Int, append: Boolean, refreshing: Boolean = false) {
        if (!append) generation++
        val myGeneration = generation
        val query = mutableState.value.query
        val employmentType = mutableState.value.employmentType
        val workplaceType = mutableState.value.workplaceType
        val location = mutableState.value.location
        val minimumSalary = mutableState.value.minimumSalary
        viewModelScope.launch {
            if (append) {
                mutableState.update { it.copy(loadingMore = true, loadMoreError = false) }
            } else {
                mutableState.update {
                    it.copy(
                        loading = it.data == null && !refreshing,
                        refreshing = refreshing,
                        loadingMore = false,
                        loadMoreError = false,
                        message = null,
                    )
                }
            }
            when (val result = repository.recommendations(query, employmentType, workplaceType, location,
                minimumSalary, page, PAGE_SIZE)) {
                is ApiResult.Success -> {
                    if (myGeneration != generation) return@launch
                    val envelope = result.value
                    val newJobs = envelope.data.map(::toUiJob)
                    mutableState.update { current ->
                        val merged = if (append) {
                            val seen = current.data?.jobs.orEmpty().map { it.jobId }.toMutableSet()
                            current.data?.jobs.orEmpty() + newJobs.filter { seen.add(it.jobId) }
                        } else {
                            newJobs
                        }
                        current.copy(
                            loading = false,
                            refreshing = false,
                            loadingMore = false,
                            loadMoreError = false,
                            message = null,
                            data = JobFeedData(
                                "Search recommended jobs",
                                merged,
                                envelope.meta.source,
                                envelope.meta.modelVersion,
                            ),
                            page = page,
                            hasNext = envelope.meta.hasNext,
                            total = envelope.meta.total,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    if (myGeneration != generation) return@launch
                    mutableState.update { current ->
                        if (append) {
                            current.copy(loadingMore = false, loadMoreError = true)
                        } else {
                            current.copy(
                                loading = false,
                                refreshing = false,
                                loadingMore = false,
                                loadMoreError = false,
                                message = if (result.code == "RESUME_REQUIRED")
                                    "Create your resume to receive personalized recommendations."
                                else result.message,
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 10

        fun factory(repository: CandidateJobRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = JobFeedViewModel(repository) as T
        }
    }
}

data class JobDetailUiState(
    val loading: Boolean = true,
    val data: JobDetailData? = null,
    val message: String? = null,
    val notFound: Boolean = false,
    val isSaved: Boolean = false,
    val saving: Boolean = false,
    val saveError: String? = null,
    val startingConversation: Boolean = false,
    val conversationError: String? = null,
    val conversationId: String? = null,
)

class JobDetailViewModel(
    private val jobId: String,
    private val repository: CandidateJobRepository,
    private val conversations: CandidateConversationRepository? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(JobDetailUiState())
    val state: StateFlow<JobDetailUiState> = mutableState.asStateFlow()

    init { load() }
    fun retry() = load()

    fun toggleSave() {
        val snapshot = mutableState.value
        val jobId = snapshot.data?.job?.jobId ?: return
        if (snapshot.saving) return
        val targetSaved = !snapshot.isSaved
        mutableState.update { it.copy(isSaved = targetSaved, saving = true, saveError = null) }
        viewModelScope.launch {
            val result = if (targetSaved) repository.saveJob(jobId) else repository.unsaveJob(jobId)
            mutableState.update { state ->
                when (result) {
                    is ApiResult.Success -> state.copy(saving = false)
                    is ApiResult.Failure -> state.copy(
                        isSaved = !targetSaved, saving = false, saveError = result.message,
                    )
                }
            }
        }
    }

    /** Opens an idempotent pre-application conversation and leaves message delivery to ChatViewModel. */
    fun messageRecruiter() {
        val job = mutableState.value.data?.job ?: return
        if (mutableState.value.startingConversation) return
        val conversationRepository = conversations
        if (conversationRepository == null) {
            mutableState.update { it.copy(conversationError = "Messaging is unavailable right now.") }
            return
        }
        mutableState.update { it.copy(startingConversation = true, conversationError = null) }
        viewModelScope.launch {
            when (val result = conversationRepository.startInquiry(job.jobId)) {
                is ApiResult.Success -> mutableState.update {
                    it.copy(startingConversation = false, conversationId = result.value.conversationId)
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(startingConversation = false, conversationError = result.message)
                }
            }
        }
    }

    fun consumeConversationNavigation() = mutableState.update { it.copy(conversationId = null) }

    private fun load() {
        viewModelScope.launch {
            mutableState.value = JobDetailUiState(loading = true)
            when (val result = repository.job(jobId)) {
                is ApiResult.Success -> {
                    val detail = result.value
                    mutableState.value = JobDetailUiState(
                        data = toUiDetail(detail.job, detail.matchAnalysis, detail.applicationState),
                        loading = false,
                        isSaved = detail.isSaved,
                    )
                }
                is ApiResult.Failure -> mutableState.value = JobDetailUiState(
                    loading = false, message = result.message, notFound = result.statusCode == 404,
                )
            }
        }
    }

    companion object {
        fun factory(jobId: String, repository: CandidateJobRepository,
                    conversations: CandidateConversationRepository? = null): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = JobDetailViewModel(jobId, repository, conversations) as T
            }
    }
}

data class SavedJobsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val jobs: List<Job> = emptyList(),
    val message: String? = null,
    val page: Int = 1,
    val hasNext: Boolean = false,
    val total: Int = 0,
    val loadingMore: Boolean = false,
    val loadMoreError: Boolean = false,
    val savingJobs: Set<String> = emptySet(),
    val saveError: String? = null,
)

class SavedJobsViewModel(private val repository: CandidateJobRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(SavedJobsUiState())
    val state: StateFlow<SavedJobsUiState> = mutableState.asStateFlow()

    private var generation = 0

    init { load() }
    fun retry() = load()
    fun refresh() = load(refreshing = true)

    fun loadMore() {
        val snapshot = mutableState.value
        if (snapshot.loading || snapshot.refreshing || snapshot.loadingMore || snapshot.loadMoreError) return
        if (!snapshot.hasNext) return
        loadPage(snapshot.page + 1, append = true)
    }

    fun retryLoadMore() {
        val snapshot = mutableState.value
        if (snapshot.loading || snapshot.refreshing || snapshot.loadingMore) return
        if (!snapshot.hasNext) return
        loadPage(snapshot.page + 1, append = true)
    }

    fun unsave(jobId: String) {
        val snapshot = mutableState.value
        if (jobId in snapshot.savingJobs) return
        val original = snapshot.jobs.firstOrNull { it.jobId == jobId } ?: return
        val originalIndex = snapshot.jobs.indexOf(original)
        mutableState.update { state ->
            state.copy(
                jobs = state.jobs.filter { it.jobId != jobId },
                savingJobs = state.savingJobs + jobId,
                saveError = null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.unsaveJob(jobId)) {
                is ApiResult.Success -> mutableState.update { it.copy(savingJobs = it.savingJobs - jobId) }
                is ApiResult.Failure -> mutableState.update { state ->
                    val restored = state.jobs.toMutableList()
                    restored.add(originalIndex.coerceAtMost(restored.size), original)
                    state.copy(
                        jobs = restored,
                        savingJobs = state.savingJobs - jobId,
                        saveError = result.message,
                    )
                }
            }
        }
    }

    private fun load(refreshing: Boolean = false) = loadPage(page = 1, append = false, refreshing = refreshing)

    private fun loadPage(page: Int, append: Boolean, refreshing: Boolean = false) {
        if (!append) generation++
        val myGeneration = generation
        viewModelScope.launch {
            if (append) {
                mutableState.update { it.copy(loadingMore = true, loadMoreError = false) }
            } else {
                mutableState.update {
                    it.copy(
                        loading = it.jobs.isEmpty() && !refreshing,
                        refreshing = refreshing,
                        loadingMore = false,
                        loadMoreError = false,
                        message = null,
                    )
                }
            }
            when (val result = repository.savedJobs(page, PAGE_SIZE)) {
                is ApiResult.Success -> {
                    if (myGeneration != generation) return@launch
                    val pageJobs = result.value.jobs.map(::toUiJob)
                    mutableState.update { current ->
                        val merged = if (append) {
                            val seen = current.jobs.map { it.jobId }.toMutableSet()
                            current.jobs + pageJobs.filter { seen.add(it.jobId) }
                        } else pageJobs
                        current.copy(
                            loading = false,
                            refreshing = false,
                            loadingMore = false,
                            loadMoreError = false,
                            message = null,
                            jobs = merged,
                            page = page,
                            hasNext = result.value.meta.hasNext,
                            total = result.value.meta.total,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    if (myGeneration != generation) return@launch
                    mutableState.update { current ->
                        if (append) {
                            current.copy(loadingMore = false, loadMoreError = true)
                        } else {
                            current.copy(
                                loading = false, refreshing = false, loadingMore = false,
                                loadMoreError = false, message = result.message,
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 20

        fun factory(repository: CandidateJobRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SavedJobsViewModel(repository) as T
        }
    }
}

private fun toUiJob(job: CandidateJob) = Job(
    jobId = job.jobId,
    title = job.title,
    company = job.company.name,
    companyInitial = job.company.name.take(1).uppercase(),
    companyMeta = listOfNotNull(job.company.stage, job.company.employeeRange).joinToString(" · ").ifBlank { "Verified company" },
    salary = formatSalary(job.salary.currency, job.salary.min.toLong(), job.salary.max.toLong(), job.salary.period),
    skills = job.skills,
    match = job.matchScore,
    recruiter = job.recruiter?.let { RecruiterContact(it.recruiterId, it.fullName, it.title, it.avatarUrl) },
    companyId = job.company.companyId,
    isSaved = job.isSaved ?: false,
)

private fun toUiJob(job: RecommendedJob) = Job(
    jobId = job.jobId,
    title = job.title,
    company = job.companyName,
    companyInitial = job.companyName.take(1).uppercase(),
    companyMeta = job.location,
    salary = formatSalary(job.salaryCurrency, job.salaryMin.toLong(), job.salaryMax.toLong(), job.salaryPeriod),
    skills = job.skills,
    match = job.matchScore,
    recruiter = job.recruiter?.let { RecruiterContact(it.recruiterId, it.fullName, it.title, it.avatarUrl) },
    companyId = job.companyId,
    isSaved = job.isSaved ?: false,
)

internal fun formatSalary(currency: String, minimum: Long, maximum: Long, period: String): String {
    val symbol = if (currency.equals("SGD", ignoreCase = true)) "S$" else currency
    val cadence = when (period.uppercase()) {
        "MONTH" -> "month"
        "YEAR" -> "year"
        "DAY" -> "day"
        "HOUR" -> "hour"
        else -> period.lowercase()
    }
    return "$symbol%,d–%,d / $cadence".format(minimum, maximum)
}

private fun toUiDetail(job: CandidateJob, analysis: MatchAnalysis?,
                       applicationState: com.adproject.candidate.data.contract.CandidateJobApplicationState) = JobDetailData(
    job = toUiJob(job),
    location = job.location,
    employmentType = job.employmentType.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
    workplace = job.workplaceType.name.lowercase().replaceFirstChar(Char::uppercase),
    strongMatches = analysis?.strongMatches?.joinToString("\n") { "• $it" }.orEmpty(),
    gap = analysis?.gaps?.joinToString("\n") { "• $it" }.orEmpty(),
    description = job.description,
    requirements = job.requirements,
    skills = job.skills,
    deadline = job.deadline,
    publishedAt = job.publishedAt,
    matchAnalysisAvailable = analysis != null,
    applicationState = applicationState,
)
