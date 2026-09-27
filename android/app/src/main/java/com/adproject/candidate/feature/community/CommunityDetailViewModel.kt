package com.adproject.candidate.feature.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adproject.candidate.data.api.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommunityDetailUiState(
    val post: CommunityPost? = null,
    val comments: List<CommunityComment> = emptyList(),
    val page: Int = 0,
    val hasNext: Boolean = false,
    val loading: Boolean = true,
    val loadingComments: Boolean = false,
    val loadingMore: Boolean = false,
    val failedCommentPage: Int? = null,
    val liking: Boolean = false,
    val submitting: Boolean = false,
    val commentDraft: String = "",
    val error: String? = null,
    val commentError: String? = null,
    val startingMessage: Boolean = false,
    val directConversationId: String? = null,
    val messageError: String? = null,
)

class CommunityDetailViewModel(private val postId: String, private val repository: CommunityRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(CommunityDetailUiState())
    val state: StateFlow<CommunityDetailUiState> = mutableState.asStateFlow()

    init { retry() }

    fun retry() {
        loadPost()
        loadComments(1, append = false)
    }

    fun toggleLike() {
        val post = mutableState.value.post ?: return
        if (mutableState.value.liking) return
        mutableState.update { it.copy(liking = true, error = null) }
        viewModelScope.launch {
            val result = if (post.likedByCurrentUser) repository.unlike(postId) else repository.like(postId)
            when (result) {
                is ApiResult.Success -> mutableState.update {
                    it.copy(liking = false, post = it.post?.copy(likeCount = result.value.likeCount, likedByCurrentUser = result.value.likedByCurrentUser))
                }
                is ApiResult.Failure -> {
                    mutableState.update { it.copy(liking = false, error = result.message) }
                    loadPost(clearError = false) // failure never leaves a locally guessed Like state
                }
            }
        }
    }

    fun updateComment(value: String) = mutableState.update { it.copy(commentDraft = value, commentError = null) }
    fun loadMore() {
        val current = mutableState.value
        if (current.hasNext && !current.loadingMore && !current.loadingComments) loadComments(current.page + 1, append = true)
    }
    fun retryComments() {
        val failedPage = mutableState.value.failedCommentPage ?: return
        loadComments(failedPage, append = failedPage > 1)
    }

    fun publishComment() {
        val raw = mutableState.value.commentDraft
        val normalized = normalizeCommunityText(raw)
        val count = normalized.codePointCount(0, normalized.length)
        if (mutableState.value.submitting || count !in 1..500) {
            if (count !in 1..500) mutableState.update { it.copy(commentError = "Comment must contain 1–500 characters.") }
            return
        }
        mutableState.update { it.copy(submitting = true, commentError = null) }
        viewModelScope.launch {
            when (val result = repository.createComment(postId, normalized)) {
                is ApiResult.Success -> mutableState.update {
                    val existing = it.comments.filterNot { value -> value.id == result.value.comment.id }
                    it.copy(
                        comments = existing + result.value.comment,
                        post = it.post?.copy(commentCount = result.value.commentCount),
                        commentDraft = "", submitting = false,
                    )
                }
                is ApiResult.Failure -> mutableState.update { it.copy(submitting = false, commentError = result.message) }
            }
        }
    }

    fun messageAuthor() {
        if (mutableState.value.startingMessage) return
        mutableState.update { it.copy(startingMessage=true,messageError=null) }
        viewModelScope.launch { when(val result=repository.startDirect(postId)) {
            is ApiResult.Success -> mutableState.update { it.copy(startingMessage=false,directConversationId=result.value.conversationId) }
            is ApiResult.Failure -> mutableState.update { it.copy(startingMessage=false,messageError=result.message) }
        } }
    }

    /** Consumes one-time navigation so returning from chat cannot open it again. */
    fun consumeDirectConversationNavigation() = mutableState.update { it.copy(directConversationId = null) }

    private fun loadPost(clearError: Boolean = true) = viewModelScope.launch {
        mutableState.update { it.copy(loading = true, error = if (clearError) null else it.error) }
        when (val result = repository.post(postId)) {
            is ApiResult.Success -> mutableState.update { it.copy(post = result.value, loading = false) }
            is ApiResult.Failure -> mutableState.update { it.copy(loading = false, error = result.message) }
        }
    }

    private fun loadComments(page: Int, append: Boolean) = viewModelScope.launch {
        mutableState.update { it.copy(loadingComments = !append, loadingMore = append, commentError = null) }
        when (val result = repository.comments(postId, page)) {
            is ApiResult.Success -> mutableState.update {
                it.copy(
                    comments = if (append) (it.comments + result.value.comments).distinctBy(CommunityComment::id) else result.value.comments,
                    page = result.value.meta.page, hasNext = result.value.meta.hasNext,
                    loadingComments = false, loadingMore = false, failedCommentPage = null,
                )
            }
            is ApiResult.Failure -> mutableState.update {
                it.copy(loadingComments = false, loadingMore = false, commentError = result.message, failedCommentPage = page)
            }
        }
    }

    companion object {
        fun factory(postId: String, repository: CommunityRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = CommunityDetailViewModel(postId, repository) as T
        }
    }
}

internal fun normalizeCommunityText(value: String): String = value.trim { it.isWhitespace() }
