package com.adproject.candidate.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.adproject.candidate.R
import com.adproject.candidate.core.designsystem.FigmaSvg
import coil3.compose.AsyncImage
import com.adproject.candidate.core.designsystem.AdBackground
import com.adproject.candidate.core.designsystem.AdBottomBar
import com.adproject.candidate.core.designsystem.AdCard
import com.adproject.candidate.core.designsystem.AdMuted
import com.adproject.candidate.core.designsystem.AdTeal
import com.adproject.candidate.core.designsystem.AdTealSoft
import com.adproject.candidate.core.designsystem.AdText
import com.adproject.candidate.core.designsystem.AdTopBar
import com.adproject.candidate.core.designsystem.MainTab
import com.adproject.candidate.core.designsystem.PrimaryButton
import com.adproject.candidate.core.designsystem.SecondaryButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class CommunityScreenMode { LOADING, ERROR, EMPTY, CONTENT }
fun communityScreenMode(state: CommunityUiState) = when {
    state.loading && state.posts.isEmpty() -> CommunityScreenMode.LOADING
    state.loadError != null && state.posts.isEmpty() -> CommunityScreenMode.ERROR
    state.posts.isEmpty() -> CommunityScreenMode.EMPTY
    else -> CommunityScreenMode.CONTENT
}

@Composable
fun CommunityScreen(
    state: CommunityUiState,
    onTab: (MainTab) -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onCategory: (CommunityCategory?) -> Unit,
    onCreate: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onPost: (String) -> Unit,
) {
    val length = state.draft.codePointCount(0, state.draft.length)
    Scaffold(
        containerColor = AdBackground,
        topBar = { AdTopBar("Community") },
        bottomBar = { AdBottomBar(MainTab.Community, onTab) },
        floatingActionButton = { FloatingActionButton(onClick = onCreate, containerColor = AdTealSoft) {
            FigmaSvg(R.raw.hirex_add, "Create post", Modifier.size(26.dp))
        } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(state.query,onQuery,Modifier.weight(1f),placeholder={Text("Search Community")},singleLine=true)
                    FigmaSvg(R.raw.hirex_search,"Search posts",Modifier.size(28.dp).clickable(onClick=onSearch))
                }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    FilterChip(selected=state.category==null,onClick={onCategory(null)},label={Text("All")})
                    CommunityCategory.entries.forEach { category -> FilterChip(selected=state.category==category,onClick={onCategory(category)},label={Text(category.label())}) }
                }
            } }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.End) {
                    SecondaryButton(if (state.refreshing) "Refreshing…" else "Refresh", onRefresh, enabled = !state.refreshing)
                }
            }
            when (communityScreenMode(state)) {
                CommunityScreenMode.LOADING -> item { StateBox { CircularProgressIndicator(color = AdTeal) } }
                CommunityScreenMode.ERROR -> item { StateBox {
                    Text(state.loadError ?: "Unable to load Community", color = AdMuted)
                    SecondaryButton("Try again", onRetry)
                } }
                CommunityScreenMode.EMPTY -> item { StateBox {
                    Text("No posts yet", color = AdText, fontWeight = FontWeight.Bold)
                    Text("Start the Community conversation.", color = AdMuted, fontSize = 12.sp)
                } }
                CommunityScreenMode.CONTENT -> {
                    state.loadError?.let { item { Text(it, Modifier.padding(horizontal = 18.dp), color = Color(0xFFB42318), fontSize = 12.sp) } }
                    items(state.posts, key = CommunityPost::id) { post -> CommunityPostCard(post) { onPost(post.id) } }
                    if (state.hasNext) item {
                        SecondaryButton(
                            if (state.loadingMore) "Loading…" else "Load more", onLoadMore,
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp), enabled = !state.loadingMore,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun CommunityCreatePostScreen(state: CommunityUiState, onBack: () -> Unit, onDraft: (String) -> Unit,
                              onCategory: (CommunityCategory?) -> Unit, onImages: (List<CommunityImageUpload>) -> Unit,
                              onPublish: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val selected = uris.take(4).mapNotNull { uri -> runCatching {
            val type = context.contentResolver.getType(uri) ?: return@runCatching null
            if (type !in setOf("image/png","image/jpeg","image/webp")) return@runCatching null
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            if (bytes.size > 5 * 1024 * 1024) return@runCatching null
            CommunityImageUpload(bytes,type)
        }.getOrNull() }
        onImages(selected)
    }
    val length=state.draft.codePointCount(0,state.draft.length)
    Scaffold(containerColor=AdBackground,topBar={AdTopBar("Create post",onBack)}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("CATEGORY",color=AdMuted,fontSize=10.sp,fontWeight=FontWeight.Bold)
            CommunityCategoryDropdown(
                selected = state.category ?: CommunityCategory.GENERAL,
                enabled = !state.submitting,
                onSelected = onCategory,
            )
            OutlinedTextField(state.draft,onDraft,Modifier.fillMaxWidth().weight(1f),placeholder={Text("What would you like to share?")},supportingText={Text("$length / 2000")},isError=length>2000)
            SecondaryButton("Choose images (${state.images.size}/4)",{launcher.launch("image/*")},Modifier.fillMaxWidth(),enabled=!state.submitting)
            if (state.images.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(state.images) { index, image ->
                        Column(Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AsyncImage(
                                model = image.bytes,
                                contentDescription = "Selected image ${index + 1}",
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Text(
                                "Remove",
                                Modifier.fillMaxWidth().clickable(enabled = !state.submitting) {
                                    onImages(state.images.filterIndexed { selectedIndex, _ -> selectedIndex != index })
                                },
                                color = AdTeal,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
            state.publishError?.let { Text(it,color=Color(0xFFB42318),fontSize=12.sp) }
            PrimaryButton(if(state.submitting)"Publishing…" else "Publish post",onPublish,Modifier.fillMaxWidth(),enabled=!state.submitting&&state.draft.isNotBlank()&&length<=2000)
        }
    }
}

@Composable
private fun CommunityCategoryDropdown(
    selected: CommunityCategory,
    enabled: Boolean,
    onSelected: (CommunityCategory) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Color.White).border(1.dp, Color(0xFFD8DEE3), RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected.label(), color = if (enabled) AdText else AdMuted, fontSize = 13.sp)
            Text("⌄", color = AdMuted, fontSize = 16.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CommunityCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.label()) },
                    onClick = {
                        expanded = false
                        onSelected(category)
                    },
                    enabled = enabled,
                )
            }
        }
    }
}

fun CommunityCategory.label()=name.replace('_',' ').lowercase().replaceFirstChar(Char::uppercase)

@Composable internal fun StateBox(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable internal fun CommunityPostCard(post: CommunityPost, onClick: (() -> Unit)? = null) {
    AdCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp).then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CommunityAvatar(post.author)
                Column {
                    Text(post.author.fullName, color = AdText, fontWeight = FontWeight.SemiBold)
                    Text(post.author.companyName ?: post.author.role.lowercase().replaceFirstChar(Char::uppercase), color = AdMuted, fontSize = 11.sp)
                }
            }
            Text(post.body, color = AdText, lineHeight = 21.sp)
            if (post.images.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(post.images, key = CommunityImage::imageId) { image ->
                        AsyncImage(
                            model = image.url,
                            contentDescription = "Post attachment",
                            modifier = Modifier.size(width = 148.dp, height = 104.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(localTime(post.createdAt), color = AdMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    FigmaSvg(if (post.likedByCurrentUser) R.raw.hirex_like_active else R.raw.hirex_like_inactive,
                        if (post.likedByCurrentUser) "Liked" else "Not liked", Modifier.size(18.dp))
                    Text("${post.likeCount}   Comments ${post.commentCount}", color = AdMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable internal fun CommunityAvatar(author: CommunityAuthor) {
    if (author.avatarUrl != null) {
        AsyncImage(author.avatarUrl, author.fullName, Modifier.size(42.dp).clip(CircleShape))
    } else {
        Box(Modifier.size(42.dp).clip(CircleShape).background(AdTealSoft), contentAlignment = Alignment.Center) {
            Text(author.fullName.take(1).uppercase().ifBlank { "?" }, color = AdTeal, fontWeight = FontWeight.Bold)
        }
    }
}

internal fun localTime(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value))
}.getOrDefault(value)
