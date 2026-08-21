package com.adproject.candidate.feature.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adproject.candidate.R
import com.adproject.candidate.core.designsystem.AdBackground
import com.adproject.candidate.core.designsystem.AdCard
import com.adproject.candidate.core.designsystem.AdMuted
import com.adproject.candidate.core.designsystem.AdTeal
import com.adproject.candidate.core.designsystem.AdTealDark
import com.adproject.candidate.core.designsystem.AdTealSoft
import com.adproject.candidate.core.designsystem.AdText
import com.adproject.candidate.core.designsystem.AdTopBar
import com.adproject.candidate.core.designsystem.FigmaSvg
import com.adproject.candidate.core.designsystem.PrimaryButton
import com.adproject.candidate.core.designsystem.SecondaryButton
import com.adproject.candidate.core.designsystem.TagChip
import com.adproject.candidate.data.contract.CandidateJobApplicationState

@Composable
fun JobDetailScreen(state: JobDetailUiState, onBack: () -> Unit, onRetry: () -> Unit,
                    onApply: (String) -> Unit, onViewCompany: (String) -> Unit,
                    onViewRecruiter: (String) -> Unit, onToggleSave: () -> Unit,
                    onMessageRecruiter: () -> Unit = {}) {
    when {
        state.loading -> Box(Modifier.fillMaxSize().background(AdBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AdTeal)
        }
        state.data == null -> Column(Modifier.fillMaxSize().background(AdBackground)) {
            AdTopBar("Job details", onBack)
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(if (state.notFound) "This job is no longer available." else state.message.orEmpty(),
                    color = AdMuted, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                if (!state.notFound) PrimaryButton("Try again", onRetry)
            }
        }
        else -> JobDetailContent(state, onBack, onApply, onViewCompany, onViewRecruiter, onToggleSave, onMessageRecruiter)
    }
}

@Composable
private fun JobDetailContent(state: JobDetailUiState, onBack: () -> Unit, onApply: (String) -> Unit,
                             onViewCompany: (String) -> Unit, onViewRecruiter: (String) -> Unit,
                             onToggleSave: () -> Unit, onMessageRecruiter: () -> Unit) {
    val data = state.data ?: return
    Column(Modifier.fillMaxSize().background(AdBackground)) {
        AdTopBar("Job details", onBack) {
            if (state.saving) CircularProgressIndicator(Modifier.size(22.dp), color = AdTeal, strokeWidth = 2.dp)
            else FigmaSvg(if (state.isSaved) R.raw.hirex_star_active else R.raw.hirex_star_inactive,
                if (state.isSaved) "Remove saved job" else "Save job",
                Modifier.size(28.dp).clickable(onClick = onToggleSave))
        }
        state.saveError?.let { message ->
            Text(message, Modifier.fillMaxWidth().background(Color.White)
                .padding(horizontal = 18.dp, vertical = 6.dp),
                color = Color(0xFFB42318), fontSize = 12.sp)
        }
        state.conversationError?.let { message ->
            Text(message, Modifier.fillMaxWidth().background(Color.White)
                .padding(horizontal = 18.dp, vertical = 6.dp),
                color = Color(0xFFB42318), fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(data.job.match?.let { "AI Match $it%" } ?: "Match unavailable", {},
                Modifier.weight(1f), enabled = false)
            val canApply = data.applicationState == CandidateJobApplicationState.NOT_APPLIED
            PrimaryButton(
                if (canApply) "Apply" else data.applicationState.displayLabel(),
                { onApply(data.job.jobId) }, Modifier.weight(1f), enabled = canApply,
            )
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AdCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.clickable { onViewCompany(data.job.companyId) }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(AdTealSoft), contentAlignment = Alignment.Center) {
                            Text(data.job.companyInitial, color = AdTeal, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(data.job.company, color = Color(0xFF111827), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(data.job.companyMeta, color = AdMuted, fontSize = 12.sp)
                        }
                    }
                    Text(data.job.title, color = Color(0xFF111827), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(data.job.salary, color = AdTeal, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${data.location} · ${data.employmentType} · ${data.workplace}", color = AdMuted, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        data.job.skills.forEach { TagChip(it) }
                    }
                }
            }
            if (data.matchAnalysisAvailable) AdCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("AI Match Analysis", color = Color(0xFF111827), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        data.job.match?.let { TagChip("$it% match", accent = true) }
                    }
                    Text("ML model compares your resume skills and experience with this job.", color = AdMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    if (data.strongMatches.isNotBlank()) {
                        Text("Strong matches\n${data.strongMatches}", color = AdTealDark, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    if (data.gap.isNotBlank()) {
                        Text("Gaps\n${data.gap}", color = Color(0xFFFF8500), fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            } else AdCard(Modifier.fillMaxWidth()) {
                Text("Match analysis is not available yet.", Modifier.padding(16.dp), color = AdMuted, fontSize = 12.sp)
            }
            AdCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Job description", color = Color(0xFF111827), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(data.description, Modifier.fillMaxWidth(), color = AdMuted, fontSize = 12.sp,
                        lineHeight = 17.sp, softWrap = true)
                    if (data.requirements.isNotEmpty()) {
                        Text("Requirements", color = Color(0xFF111827), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            data.requirements.forEach { requirement ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    Text("•", Modifier.padding(end = 8.dp), color = AdTealDark, fontSize = 12.sp)
                                    Text(requirement, Modifier.weight(1f), color = AdTealDark, fontSize = 12.sp,
                                        lineHeight = 18.sp, softWrap = true)
                                }
                            }
                        }
                    }
                    data.deadline?.let { Text("Deadline: $it", color = AdMuted, fontSize = 10.sp) }
                    data.publishedAt?.let { Text("Published: $it", color = AdMuted, fontSize = 10.sp) }
                }
            }
            data.job.recruiter?.let { recruiter -> AdCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(AdTealSoft)
                        .clickable { onViewRecruiter(recruiter.recruiterId) }, contentAlignment = Alignment.Center) {
                        Text(recruiter.fullName.take(1), color = AdTeal)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f).clickable { onViewRecruiter(recruiter.recruiterId) }) {
                        Text(recruiter.fullName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${recruiter.title} · ${data.job.company}", color = AdMuted, fontSize = 10.sp)
                    }
                    PrimaryButton(if (state.startingConversation) "Opening…" else "Message", onMessageRecruiter,
                        enabled = !state.startingConversation)
                }
            } }
            Spacer(Modifier.height(10.dp))
        }
    }
}

private fun CandidateJobApplicationState.displayLabel() = when (this) {
    CandidateJobApplicationState.NOT_APPLIED -> "Apply"
    CandidateJobApplicationState.APPLIED -> "Applied"
    CandidateJobApplicationState.IN_REVIEW -> "In review"
    CandidateJobApplicationState.INTERVIEW -> "Interview"
    CandidateJobApplicationState.OFFERED -> "Offer received"
    CandidateJobApplicationState.REJECTED -> "Rejected"
    CandidateJobApplicationState.WITHDRAWN -> "Withdrawn"
}
