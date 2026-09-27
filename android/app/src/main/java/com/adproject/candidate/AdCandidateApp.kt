package com.adproject.candidate

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adproject.candidate.core.designsystem.MainTab
import com.adproject.candidate.feature.applications.RealApplicationSubmittedScreen
import com.adproject.candidate.feature.applications.RealApplyConfirmationScreen
import com.adproject.candidate.feature.applications.ApplicationViewModel
import com.adproject.candidate.feature.applications.ApplicationListViewModel
import com.adproject.candidate.feature.applications.ApplicationDetailViewModel
import com.adproject.candidate.feature.applications.RealMyApplicationsScreen
import com.adproject.candidate.feature.applications.RealApplicationDetailScreen
import com.adproject.candidate.feature.auth.CreateAccountScreen
import com.adproject.candidate.feature.auth.SignInScreen
import com.adproject.candidate.feature.auth.PasswordResetScreen
import com.adproject.candidate.feature.auth.CandidateOnboardingScreen
import com.adproject.candidate.feature.auth.AuthViewModel
import com.adproject.candidate.feature.agent.AgentScreen
import com.adproject.candidate.feature.agent.AgentViewModel
import com.adproject.candidate.feature.jobs.JobDetailScreen
import com.adproject.candidate.feature.jobs.JobFeedScreen
import com.adproject.candidate.feature.jobs.SavedJobsScreen
import com.adproject.candidate.feature.jobs.JobFeedViewModel
import com.adproject.candidate.feature.jobs.JobDetailViewModel
import com.adproject.candidate.feature.jobs.SavedJobsViewModel
import com.adproject.candidate.feature.messages.MessagesScreen
import com.adproject.candidate.feature.messages.ChatScreen
import com.adproject.candidate.feature.messages.MessagesViewModel
import com.adproject.candidate.feature.messages.ChatViewModel
import com.adproject.candidate.core.network.CandidateAppContainer
import com.adproject.candidate.feature.profile.RealProfileScreen
import com.adproject.candidate.feature.profile.RealProfileEditScreen
import com.adproject.candidate.feature.profile.RealResumeEditScreen
import com.adproject.candidate.feature.profile.CandidateProfileViewModel
import com.adproject.candidate.feature.profile.CandidateResumeViewModel
import com.adproject.candidate.feature.profile.JobPreferenceViewModel
import com.adproject.candidate.feature.profile.JobPreferencesScreen
import com.adproject.candidate.feature.profile.RecruiterPublicProfileScreen
import com.adproject.candidate.feature.profile.CompanyPublicProfileScreen
import com.adproject.candidate.feature.profile.RecruiterPublicProfileViewModel
import com.adproject.candidate.feature.profile.CompanyPublicProfileViewModel
import com.adproject.candidate.feature.community.CommunityScreen
import com.adproject.candidate.feature.community.CommunityViewModel
import com.adproject.candidate.feature.community.CommunityDetailScreen
import com.adproject.candidate.feature.community.CommunityDetailViewModel
import com.adproject.candidate.feature.community.CommunityCreatePostScreen
import com.adproject.candidate.feature.community.CommunityDirectViewModel
import com.adproject.candidate.feature.community.CommunityDirectMessageScreen
import com.adproject.candidate.data.api.CandidateRepository
import com.adproject.candidate.data.api.FakeCandidateRepository

private object Route {
    const val SignIn = "sign-in"
    const val CreateAccount = "create-account"
    const val PasswordReset = "password-reset"
    const val Onboarding = "onboarding"
    const val Jobs = "jobs"
    const val Messages = "messages"
    const val ChatDetail = "chat-detail/{conversationId}"
    const val Profile = "profile"
    const val ProfileEdit = "profile/edit"
    const val ResumeEdit = "resume/edit"
    const val Applications = "applications"
    const val ApplicationDetail = "application-detail/{applicationId}"
    const val JobDetail = "job-detail/{jobId}"
    const val Apply = "apply/{jobId}"
    const val Submitted = "submitted/{jobId}"
    const val JobPreferences = "job-preferences"
    const val SavedJobs = "saved-jobs"
    const val RecruiterProfile = "recruiter/{recruiterId}"
    const val CompanyProfile = "company/{companyId}"
    const val Community = "community"
    const val CommunityDetail = "community/{postId}"
    const val CommunityCreate = "community-create"
    const val CommunityDirect = "community-direct/{conversationId}"
    const val Agent = "agent"

    fun jobDetail(id: String) = "job-detail/$id"
    fun chatDetail(id: String) = "chat-detail/$id"
    fun apply(id: String) = "apply/$id"
    fun submitted(id: String) = "submitted/$id"
    fun applicationDetail(id: String) = "application-detail/$id"
    fun recruiterProfile(id: String) = "recruiter/$id"
    fun companyProfile(id: String) = "company/$id"
    fun communityDetail(id: String) = "community/$id"
    fun communityDirect(id: String) = "community-direct/$id"
}

/**
 * Bridges the Compose lifecycle (foreground + route visibility) into a ViewModel's
 * start/stop callbacks without firing any network request from the Composable layer.
 *
 * `LocalLifecycleOwner` inside a NavHost destination is that destination's
 * `NavBackStackEntry`, whose lifecycle is capped by the host activity. ON_START/ON_STOP
 * therefore fire when the app backgrounds or the destination is left, and again when the
 * destination becomes visible or the app returns to the foreground.
 */
@Composable
private fun ScreenLifecyclePolling(onStarted: () -> Unit, onStopped: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> onStarted()
                Lifecycle.Event.ON_STOP -> onStopped()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) onStarted()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onStopped()
        }
    }
}

@Composable
fun AdCandidateApp(
    fakeCandidateFeatures: CandidateRepository = FakeCandidateRepository,
    providedContainer: CandidateAppContainer? = null,
) {
    val context = LocalContext.current
    val container = remember(providedContainer) { providedContainer ?: CandidateAppContainer(context) }
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(container.authRepository))
    val applicationViewModel: ApplicationViewModel = viewModel(factory = ApplicationViewModel.factory(
        container.candidateJobRepository, container.candidateProfileRepository,
        container.candidateResumeRepository, container.candidateApplicationRepository,
    ))
    // Shared across the Me page and its dedicated edit destinations so profile/resume state and
    // the application grouping totals persist while navigating between them.
    val profileViewModel: CandidateProfileViewModel = viewModel(
        factory = CandidateProfileViewModel.factory(container.candidateProfileRepository, container.candidateAvatarRepository),
    )
    val resumeViewModel: CandidateResumeViewModel = viewModel(
        factory = CandidateResumeViewModel.factory(container.candidateResumeRepository),
    )
    val applicationListViewModel: ApplicationListViewModel = viewModel(
        factory = ApplicationListViewModel.factory(container.candidateApplicationRepository),
    )
    val communityViewModel: CommunityViewModel = viewModel(
        factory = CommunityViewModel.factory(container.communityRepository),
    )
    val sessionActive by container.sessionManager.sessionActive.collectAsStateWithLifecycle()
    val onboardingRequired by container.sessionManager.onboardingRequired.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { container.sessionManager.load() }

    LaunchedEffect(sessionActive) {
        if (sessionActive == true) {
            profileViewModel.load()
            resumeViewModel.load()
            applicationListViewModel.load()
            communityViewModel.refresh()
        } else if (sessionActive == false) {
            authViewModel.resetForSignedOut()
            applicationViewModel.clear()
            profileViewModel.reset()
            resumeViewModel.reset()
            applicationListViewModel.reset()
            communityViewModel.reset()
        }
    }

    if (sessionActive == null || onboardingRequired == null) {
        Surface(Modifier.fillMaxSize(), color = Color.White) {}
        return
    }

    fun navigateBack(fallbackRoute: String) {
        if (!navController.navigateUp()) {
            navController.navigate(fallbackRoute) { launchSingleTop = true }
        }
    }

    fun openTab(tab: MainTab) {
        val route = when (tab) {
            MainTab.Jobs -> Route.Jobs
            MainTab.Community -> Route.Community
            MainTab.Agent -> Route.Agent
            MainTab.Messages -> Route.Messages
            MainTab.Me -> Route.Profile
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = Color.White) {
        LaunchedEffect(sessionActive, onboardingRequired) {
            if (sessionActive == true) {
                navController.navigate(if (onboardingRequired == true) Route.Onboarding else Route.Jobs) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                navController.navigate(Route.SignIn) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        NavHost(navController = navController, startDestination = when {
            sessionActive != true -> Route.SignIn
            onboardingRequired == true -> Route.Onboarding
            else -> Route.Jobs
        }) {
            composable(Route.SignIn) {
                val state by authViewModel.signIn.collectAsStateWithLifecycle()
                SignInScreen(
                    state = state,
                    onEmail = authViewModel::updateSignInEmail,
                    onPassword = authViewModel::updateSignInPassword,
                    onSignIn = authViewModel::signIn,
                    onCreateAccount = {
                        authViewModel.resetRegister()
                        navController.navigate(Route.CreateAccount)
                    },
                    onForgotPassword = { navController.navigate(Route.PasswordReset) },
                )
            }
            composable(Route.PasswordReset) {
                val state by authViewModel.reset.collectAsStateWithLifecycle()
                PasswordResetScreen(
                    state = state,
                    onEmail = authViewModel::updateResetEmail,
                    onCode = authViewModel::updateResetCode,
                    onPassword = authViewModel::updateResetPassword,
                    onConfirm = authViewModel::updateResetConfirm,
                    onRequest = authViewModel::requestPasswordReset,
                    onReset = authViewModel::confirmPasswordReset,
                    onResend = authViewModel::resendPasswordReset,
                    onBackToSignIn = {
                        authViewModel.restartPasswordReset()
                        navController.navigate(Route.SignIn) { popUpTo(Route.SignIn) { inclusive = false } }
                    },
                )
            }
            composable(Route.Onboarding) {
                val state by authViewModel.onboarding.collectAsStateWithLifecycle()
                CandidateOnboardingScreen(
                    state = state,
                    onHeadline = authViewModel::updateOnboardingHeadline,
                    onLocation = authViewModel::updateOnboardingLocation,
                    onAge = authViewModel::updateOnboardingAge,
                    onSummary = authViewModel::updateOnboardingSummary,
                    onSkills = authViewModel::updateOnboardingSkills,
                    onDesiredTitle = authViewModel::updateDesiredTitle,
                    onPreferredLocation = authViewModel::updatePreferredLocation,
                    onWorkplaceType = authViewModel::updateWorkplaceType,
                    onEmploymentType = authViewModel::updateEmploymentType,
                    onComplete = authViewModel::completeOnboarding,
                )
            }
            composable(Route.CreateAccount) {
                val state by authViewModel.register.collectAsStateWithLifecycle()
                CreateAccountScreen(
                    state = state,
                    onFullName = authViewModel::updateFullName,
                    onEmail = authViewModel::updateRegisterEmail,
                    onPassword = authViewModel::updateRegisterPassword,
                    onConfirmPassword = authViewModel::updateConfirmPassword,
                    onAgreed = authViewModel::updateAgreed,
                    onCreate = authViewModel::register,
                    onSignIn = {
                        authViewModel.resetRegister()
                        navController.popBackStack()
                    },
                )
            }
            composable(Route.Jobs) {
                val jobsViewModel: JobFeedViewModel = viewModel(
                    factory = JobFeedViewModel.factory(container.candidateJobRepository),
                )
                val state by jobsViewModel.state.collectAsStateWithLifecycle()
                JobFeedScreen(
                    state = state,
                    onQuery = jobsViewModel::updateQuery,
                    onSearch = jobsViewModel::search,
                    onEmploymentType = jobsViewModel::selectEmploymentType,
                    onWorkplaceType = jobsViewModel::selectWorkplaceType,
                    onLocation = jobsViewModel::selectLocation,
                    onMinimumSalary = jobsViewModel::selectMinimumSalary,
                    onClearFilters = jobsViewModel::clearFilters,
                    onToggleSave = jobsViewModel::toggleSave,
                    onRefresh = jobsViewModel::refresh,
                    onRetry = jobsViewModel::retry,
                    onLoadMore = jobsViewModel::loadMore,
                    onRetryLoadMore = jobsViewModel::retryLoadMore,
                    onTab = ::openTab,
                    onJob = { navController.navigate(Route.jobDetail(it)) },
                    onApplyFilters = jobsViewModel::applyFilters,
                )
            }
            composable(Route.Messages) {
                val messagesViewModel: MessagesViewModel = viewModel(
                    factory = MessagesViewModel.factory(container.candidateConversationRepository, container.communityRepository),
                )
                val state by messagesViewModel.state.collectAsStateWithLifecycle()
                ScreenLifecyclePolling(
                    onStarted = messagesViewModel::onScreenStarted,
                    onStopped = messagesViewModel::onScreenStopped,
                )
                MessagesScreen(
                    state = state,
                    onRetry = messagesViewModel::retry,
                    onRefresh = messagesViewModel::refresh,
                    onTab = ::openTab,
                    onConversation = { navController.navigate(Route.chatDetail(it)) },
                    onCommunityConversation = { navController.navigate(Route.communityDirect(it)) },
                )
            }
            composable(Route.ChatDetail) { entry ->
                val conversationId = entry.arguments?.getString("conversationId").orEmpty()
                val chatViewModel: ChatViewModel = viewModel(
                    key = "chat-$conversationId",
                    factory = ChatViewModel.factory(conversationId, container.candidateConversationRepository),
                )
                val state by chatViewModel.state.collectAsStateWithLifecycle()
                ScreenLifecyclePolling(
                    onStarted = chatViewModel::onScreenStarted,
                    onStopped = chatViewModel::onScreenStopped,
                )
                ChatScreen(
                    state = state,
                    onBack = { navigateBack(Route.Messages) },
                    onRetry = chatViewModel::retry,
                    onDraft = chatViewModel::updateDraft,
                    onSend = chatViewModel::send,
                    onSelectAttachment = chatViewModel::selectAttachment,
                    onRemoveAttachment = chatViewModel::removeAttachment,
                    onDownloadAttachment = chatViewModel::download,
                    onOpenImage = chatViewModel::openImage,
                    onConsumeDownload = chatViewModel::consumeDownload,
                    onCloseImagePreview = chatViewModel::closeImagePreview,
                    onViewJob = { navController.navigate(Route.jobDetail(it)) },
                    onViewRecruiter = { navController.navigate(Route.recruiterProfile(it)) },
                )
            }
            composable(Route.Profile) {
                val state by profileViewModel.state.collectAsStateWithLifecycle()
                val resumeState by resumeViewModel.state.collectAsStateWithLifecycle()
                val applicationsState by applicationListViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { applicationListViewModel.load() }
                ScreenLifecyclePolling(
                    onStarted = {
                        profileViewModel.refresh()
                        resumeViewModel.refresh()
                    },
                    onStopped = {},
                )
                RealProfileScreen(
                    state = state,
                    resumeState = resumeState,
                    counts = applicationsState.counts,
                    onRetry = profileViewModel::load,
                    onOpenProfile = {
                        profileViewModel.clearSaved()
                        navController.navigate(Route.ProfileEdit)
                    },
                    onOpenApplications = { navController.navigate(Route.Applications) },
                    onOpenResume = {
                        resumeViewModel.clearSaved()
                        navController.navigate(Route.ResumeEdit)
                    },
                    onOpenPreferences = { navController.navigate(Route.JobPreferences) },
                    onOpenSavedJobs = { navController.navigate(Route.SavedJobs) },
                    onOpenAgent = { navController.navigate(Route.Agent) },
                    onLogout = authViewModel::logout,
                    onTab = ::openTab,
                )
            }
            composable(Route.ProfileEdit) {
                val state by profileViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.saved) { if (state.saved) navigateBack(Route.Profile) }
                RealProfileEditScreen(
                    state = state,
                    onBack = { navigateBack(Route.Profile) },
                    onRetry = profileViewModel::load,
                    onSave = profileViewModel::save,
                    onSelectAvatar = profileViewModel::selectAvatar,
                    onUploadAvatar = profileViewModel::uploadAvatar,
                    onDeleteAvatar = profileViewModel::deleteAvatar,
                    onCancelAvatar = profileViewModel::cancelAvatar,
                    onAvatarTooLarge = profileViewModel::rejectAvatarTooLarge,
                )
            }
            composable(Route.Agent) {
                val agentViewModel: AgentViewModel = viewModel(
                    factory = AgentViewModel.factory(container.candidateAgentRepository),
                )
                val state by agentViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(agentViewModel) { agentViewModel.loadHistory() }
                AgentScreen(
                    state = state,
                    onTab = ::openTab,
                    onInstruction = agentViewModel::updateInstruction,
                    onCreate = agentViewModel::create,
                    onRefresh = agentViewModel::refresh,
                    onConfirm = agentViewModel::confirm,
                    onCancel = agentViewModel::cancel,
                    onStartAnother = agentViewModel::startNewConversation,
                    onOpenConversation = agentViewModel::openConversation,
                    onDeleteConversation = agentViewModel::deleteConversation,
                )
            }
            composable(Route.Community) {
                val state by communityViewModel.state.collectAsStateWithLifecycle()
                CommunityScreen(
                    state = state,
                    onTab = ::openTab,
                    onQuery = communityViewModel::updateQuery,
                    onSearch = communityViewModel::search,
                    onCategory = communityViewModel::selectCategory,
                    onCreate = { navController.navigate(Route.CommunityCreate) },
                    onRefresh = communityViewModel::refresh,
                    onRetry = communityViewModel::retry,
                    onLoadMore = communityViewModel::loadMore,
                    onPost = { navController.navigate(Route.communityDetail(it)) },
                )
            }
            composable(Route.CommunityCreate) {
                val state by communityViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.publishedPostId) { state.publishedPostId?.let { id -> communityViewModel.consumePublished();navController.navigate(Route.communityDetail(id)){popUpTo(Route.Community)} } }
                CommunityCreatePostScreen(state,{navigateBack(Route.Community)},communityViewModel::updateDraft,
                    communityViewModel::selectCategory,communityViewModel::updateImages,communityViewModel::publish)
            }
            composable(Route.CommunityDetail) { entry ->
                val postId = requireNotNull(entry.arguments?.getString("postId"))
                val detailViewModel: CommunityDetailViewModel = viewModel(
                    key = "community-$postId",
                    factory = CommunityDetailViewModel.factory(postId, container.communityRepository),
                )
                val state by detailViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.directConversationId) {
                    state.directConversationId?.let {
                        detailViewModel.consumeDirectConversationNavigation()
                        navController.navigate(Route.communityDirect(it))
                    }
                }
                CommunityDetailScreen(
                    state = state, onBack = {
                        state.post?.let(communityViewModel::applyPostUpdate)
                        navigateBack(Route.Community)
                    }, onRetry = detailViewModel::retry,
                    onToggleLike = detailViewModel::toggleLike, onComment = detailViewModel::updateComment,
                    onPublishComment = detailViewModel::publishComment, onLoadMore = detailViewModel::loadMore,
                    onRetryComments = detailViewModel::retryComments,
                    onMessageAuthor = detailViewModel::messageAuthor,
                )
            }
            composable(Route.CommunityDirect) { entry ->
                val id=requireNotNull(entry.arguments?.getString("conversationId"));val viewModel:CommunityDirectViewModel=viewModel(key="community-direct-$id",factory=CommunityDirectViewModel.factory(id,container.communityRepository));val state by viewModel.state.collectAsStateWithLifecycle()
                CommunityDirectMessageScreen(state,{if(!navController.popBackStack())openTab(MainTab.Messages)},viewModel::load,viewModel::updateDraft,viewModel::send)
            }
            composable(Route.ResumeEdit) {
                val state by resumeViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.saved) { if (state.saved) navigateBack(Route.Profile) }
                RealResumeEditScreen(
                    state = state,
                    onBack = { navigateBack(Route.Profile) },
                    onRetry = resumeViewModel::load,
                    onSave = resumeViewModel::save,
                )
            }
            composable(Route.Applications) { entry ->
                val state by applicationListViewModel.state.collectAsStateWithLifecycle()
                val refreshKey by entry.savedStateHandle.getStateFlow("applications-refresh", 0L)
                    .collectAsStateWithLifecycle()
                LaunchedEffect(refreshKey) {
                    if (refreshKey == 0L) applicationListViewModel.load() else applicationListViewModel.refresh()
                }
                RealMyApplicationsScreen(
                    state = state,
                    onTab = ::openTab,
                    onBack = { navigateBack(Route.Profile) },
                    onRefresh = applicationListViewModel::refresh,
                    onRetry = applicationListViewModel::retry,
                    onFilter = applicationListViewModel::selectFilter,
                    onLoadMore = applicationListViewModel::loadMore,
                    onApplication = { navController.navigate(Route.applicationDetail(it)) },
                )
            }
            composable(Route.ApplicationDetail) { entry ->
                val applicationId = entry.arguments?.getString("applicationId").orEmpty()
                val detailViewModel: ApplicationDetailViewModel = viewModel(
                    key = "application-detail-$applicationId",
                    factory = ApplicationDetailViewModel.factory(applicationId, container.candidateApplicationRepository),
                )
                val state by detailViewModel.state.collectAsStateWithLifecycle()
                RealApplicationDetailScreen(
                    state = state,
                    onBack = {
                        navController.previousBackStackEntry?.savedStateHandle
                            ?.set("applications-refresh", System.nanoTime())
                        navigateBack(Route.Applications)
                    },
                    onRetry = detailViewModel::load,
                    onRequestWithdraw = detailViewModel::requestWithdraw,
                    onDismissWithdraw = detailViewModel::dismissWithdraw,
                    onWithdrawReason = detailViewModel::updateWithdrawReason,
                    onConfirmWithdraw = detailViewModel::confirmWithdraw,
                )
            }
            composable(Route.JobDetail) { entry ->
                val jobId = entry.arguments?.getString("jobId") ?: "moonshot"
                val detailViewModel: JobDetailViewModel = viewModel(
                    key = "job-detail-$jobId",
                    factory = JobDetailViewModel.factory(jobId, container.candidateJobRepository,
                        container.candidateConversationRepository),
                )
                val state by detailViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.conversationId) {
                    state.conversationId?.let { conversationId ->
                        detailViewModel.consumeConversationNavigation()
                        navController.navigate(Route.chatDetail(conversationId))
                    }
                }
                JobDetailScreen(
                    state = state,
                    onBack = { navigateBack(Route.Jobs) },
                    onRetry = detailViewModel::retry,
                    onApply = { navController.navigate(Route.apply(it)) },
                    onViewCompany = { navController.navigate(Route.companyProfile(it)) },
                    onViewRecruiter = { navController.navigate(Route.recruiterProfile(it)) },
                    onToggleSave = detailViewModel::toggleSave,
                    onMessageRecruiter = detailViewModel::messageRecruiter,
                )
            }
            composable(Route.RecruiterProfile) { entry ->
                val recruiterId = entry.arguments?.getString("recruiterId").orEmpty()
                val recruiterViewModel: RecruiterPublicProfileViewModel = viewModel(
                    key = "recruiter-$recruiterId",
                    factory = RecruiterPublicProfileViewModel.factory(recruiterId, container.candidatePublicProfileRepository),
                )
                val state by recruiterViewModel.state.collectAsStateWithLifecycle()
                RecruiterPublicProfileScreen(
                    state = state,
                    onBack = { navigateBack(Route.Jobs) },
                    onRetry = recruiterViewModel::retry,
                )
            }
            composable(Route.CompanyProfile) { entry ->
                val companyId = entry.arguments?.getString("companyId").orEmpty()
                val companyViewModel: CompanyPublicProfileViewModel = viewModel(
                    key = "company-$companyId",
                    factory = CompanyPublicProfileViewModel.factory(companyId, container.candidatePublicProfileRepository),
                )
                val state by companyViewModel.state.collectAsStateWithLifecycle()
                CompanyPublicProfileScreen(
                    state = state,
                    onBack = { navigateBack(Route.Jobs) },
                    onRetry = companyViewModel::retry,
                )
            }
            composable(Route.Apply) { entry ->
                val jobId = entry.arguments?.getString("jobId") ?: "moonshot"
                LaunchedEffect(jobId) { applicationViewModel.start(jobId) }
                val state by applicationViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.result?.applicationId) {
                    if (state.result != null) navController.navigate(Route.submitted(jobId)) { launchSingleTop = true }
                }
                RealApplyConfirmationScreen(
                    state = state,
                    onBack = { navigateBack(Route.jobDetail(jobId)) },
                    onRetry = applicationViewModel::retryLoad,
                    onCreateResume = { navController.navigate(Route.Profile) },
                    onShareProfile = applicationViewModel::setShareProfile,
                    onSubmit = applicationViewModel::submit,
                )
            }
            composable(Route.Submitted) { entry ->
                val jobId = entry.arguments?.getString("jobId") ?: "moonshot"
                val state by applicationViewModel.state.collectAsStateWithLifecycle()
                RealApplicationSubmittedScreen(
                    application = state.result,
                    onJobs = {
                        applicationViewModel.clear()
                        navController.navigate(Route.Jobs) { popUpTo(Route.Jobs) { inclusive = true } }
                    },
                    onApplications = {
                        applicationViewModel.clear()
                        navController.navigate(Route.Applications)
                    },
                )
            }
            composable(Route.JobPreferences) {
                val preferenceViewModel: JobPreferenceViewModel = viewModel(
                    factory = JobPreferenceViewModel.factory(container.candidateRecommendationRepository),
                )
                val state by preferenceViewModel.state.collectAsStateWithLifecycle()
                JobPreferencesScreen(
                    state = state,
                    onRetry = preferenceViewModel::load,
                    onBack = { navigateBack(Route.Profile) },
                    onSave = preferenceViewModel::save,
                )
            }
            composable(Route.SavedJobs) {
                val savedJobsViewModel: SavedJobsViewModel = viewModel(
                    factory = SavedJobsViewModel.factory(container.candidateJobRepository),
                )
                val state by savedJobsViewModel.state.collectAsStateWithLifecycle()
                SavedJobsScreen(
                    state = state,
                    onBack = { navigateBack(Route.Profile) },
                    onRetry = savedJobsViewModel::retry,
                    onRefresh = savedJobsViewModel::refresh,
                    onLoadMore = savedJobsViewModel::loadMore,
                    onRetryLoadMore = savedJobsViewModel::retryLoadMore,
                    onJob = { navController.navigate(Route.jobDetail(it)) },
                    onUnsave = savedJobsViewModel::unsave,
                )
            }
        }
    }
}
