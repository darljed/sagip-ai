package dev.darl.sagip.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.darl.sagip.data.Severity
import dev.darl.sagip.ui.theme.SagipColors

/** Quick-action emergency tiles shown on the empty state. */
private val QUICK_ACTIONS = listOf(
    "Severe bleeding", "CPR", "Choking",
    "Flood coming in", "Earthquake", "Snakebite",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(state: ChatState, onSend: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Box(Modifier.fillMaxSize().background(SagipColors.CanvasGradient)) {
        Column(Modifier.fillMaxSize().padding(top = 52.dp, bottom = 16.dp)) {
            Header()

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.messages.isEmpty()) {
                    EmptyState(onPick = { onSend(it) })
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(state.messages) { _, m ->
                            when (m.role) {
                                Role.USER -> UserBubble(m)
                                Role.ASSISTANT -> AssistantBubble(m)
                            }
                        }
                    }
                }
            }

            InputBar(
                value = input,
                onValueChange = { input = it },
                enabled = !state.busy,
                onSend = {
                    if (input.isNotBlank()) { onSend(input); input = "" }
                },
            )
        }
    }
}

@Composable
private fun Header() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(SagipColors.OrbGradient))
        Spacer(Modifier.width(10.dp))
        Text("SAGIP", color = SagipColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(8.dp).clip(CircleShape).background(SagipColors.Ok))
        Spacer(Modifier.width(6.dp))
        Text("Offline · Ready", color = SagipColors.TextDim, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(SagipColors.OrbGradient))
        Spacer(Modifier.height(20.dp))
        Text("How can I help in this emergency?", color = SagipColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Works fully offline · guidance from trusted manuals", color = SagipColors.TextDim, fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QUICK_ACTIONS.forEach { label ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(SagipColors.AccentSoft)
                        .clickable { onPick(label) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(label, color = SagipColors.Text, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun UserBubble(m: Message) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                .background(SagipColors.Accent)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(m.text, color = Color.White, fontSize = 15.sp)
        }
    }
}

@Composable
private fun AssistantBubble(m: Message) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                .background(SagipColors.Surface)
                .padding(14.dp)
        ) {
            // Label row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(SagipColors.OrbGradient))
                Spacer(Modifier.width(6.dp))
                Text("SAGIP", color = SagipColors.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                if (m.streaming) {
                    Spacer(Modifier.width(6.dp)); Text("· typing…", color = SagipColors.TextDim, fontSize = 11.sp)
                }
            }

            // Severity banner (critical/urgent)
            m.severity?.let { sev ->
                if (sev == Severity.CRITICAL || sev == Severity.URGENT) {
                    Spacer(Modifier.height(8.dp))
                    SeverityBanner(sev)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(m.text.ifEmpty { "…" }, color = SagipColors.Text, fontSize = 15.sp)

            // Emergency-call line
            m.callContact?.let {
                Spacer(Modifier.height(10.dp))
                Text("📞 $it — or 911 when signal returns", color = SagipColors.SeverityUrgent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            // Citation line
            if (m.sources.isNotEmpty() && !m.streaming) {
                Spacer(Modifier.height(10.dp))
                Text("Source: " + m.sources.joinToString("; "), color = SagipColors.TextDim, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SeverityBanner(sev: Severity) {
    val (color, label) = when (sev) {
        Severity.CRITICAL -> SagipColors.SeverityCritical to "⚠ LIFE-THREATENING — act now"
        Severity.URGENT -> SagipColors.SeverityUrgent to "⚠ Urgent — act quickly"
        else -> SagipColors.SeverityInfo to "Info"
    }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InputBar(value: String, onValueChange: (String) -> Unit, enabled: Boolean, onSend: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp)).background(SagipColors.SurfaceStrong)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Describe the emergency…", color = SagipColors.TextDim, fontSize = 15.sp)
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                textStyle = TextStyle(color = SagipColors.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(SagipColors.Accent), modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("🎙", fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(if (enabled) SagipColors.Accent else SagipColors.AccentSoft)
                .clickable(enabled = enabled, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) { Text("↑", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}
