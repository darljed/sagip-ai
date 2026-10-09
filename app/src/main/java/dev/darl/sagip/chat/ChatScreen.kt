package dev.darl.sagip.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import dev.darl.sagip.data.Severity
import dev.darl.sagip.ui.theme.SagipColors

private val QUICK_ACTIONS = listOf(
    "Severe bleeding", "CPR", "Choking",
    "Flood coming in", "Earthquake", "Snakebite",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    state: ChatState,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    listening: Boolean,
    onMic: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Box(Modifier.fillMaxSize().background(SagipColors.CanvasGradient)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                // Bottom padding = max(keyboard, nav bar), NOT their sum. union() gives
                // the max, so when the keyboard is up the input sits right on it (nav bar
                // is behind the keyboard) and when it's down the input clears the nav bar.
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
        ) {
            Header(state)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.messages.isEmpty()) {
                    EmptyState(onPick = { onInputChange(it); onSend() })
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
                        item { Spacer(Modifier.height(4.dp)) }
                    }
                }
            }

            InputBar(
                value = input,
                onValueChange = onInputChange,
                enabled = !state.busy,
                onSend = onSend,
                listening = listening,
                onMic = onMic,
            )
        }
    }
}

@Composable
private fun Header(state: ChatState) {
    val (dotColor, label) = when (state.modelStatus) {
        ModelStatus.READY -> SagipColors.Ok to "Offline · ${state.modelName}"
        ModelStatus.LOADING -> SagipColors.SeverityCaution to "Loading ${state.modelName}…"
        ModelStatus.MOCK -> SagipColors.SeverityInfo to "Offline · demo mode"
        ModelStatus.ERROR -> SagipColors.SeverityUrgent to "Offline · ${state.modelName}"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(SagipColors.OrbGradient))
        Spacer(Modifier.width(10.dp))
        Text("SAGIP", color = SagipColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(6.dp))
        Text(label, color = SagipColors.TextDim, fontSize = 12.sp)
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
                    Modifier.clip(RoundedCornerShape(999.dp)).background(SagipColors.AccentSoft)
                        .clickable { onPick(label) }.padding(horizontal = 16.dp, vertical = 10.dp)
                ) { Text(label, color = SagipColors.Text, fontSize = 14.sp) }
            }
        }
    }
}

@Composable
private fun UserBubble(m: Message) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                .background(SagipColors.Accent).padding(horizontal = 16.dp, vertical = 12.dp)
        ) { Text(m.text, color = Color.White, fontSize = 15.sp) }
    }
}

@Composable
private fun AssistantBubble(m: Message) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 340.dp)
                .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                .background(SagipColors.Surface).padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(SagipColors.OrbGradient))
                Spacer(Modifier.width(6.dp))
                Text("SAGIP", color = SagipColors.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                if (m.streaming) {
                    Spacer(Modifier.width(6.dp)); Text("· typing…", color = SagipColors.TextDim, fontSize = 11.sp)
                }
            }

            m.severity?.let { sev ->
                if (sev == Severity.CRITICAL || sev == Severity.URGENT) {
                    Spacer(Modifier.height(8.dp)); SeverityBanner(sev)
                }
            }

            Spacer(Modifier.height(8.dp))
            // Render markdown (bold) instead of showing literal ** asterisks.
            Text(
                text = if (m.text.isEmpty()) androidx.compose.ui.text.AnnotatedString("…")
                       else renderInlineMarkdown(m.text),
                color = SagipColors.Text, fontSize = 15.sp,
            )

            // Tap-to-call buttons (SOP: every number is a dial button).
            // 911 is ALWAYS offered on an emergency answer; the personal contact
            // shows when the retrieved guidance is call-worthy and a contact is set.
            if (m.showCallActions) {
                Spacer(Modifier.height(12.dp))
                m.callContact?.let { label ->
                    m.callNumber?.let { number ->
                        CallButton(label = label, number = number, color = SagipColors.Accent)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                CallButton(label = "Call 911 — Emergency", number = "911", color = SagipColors.SeverityCritical)
                Spacer(Modifier.height(2.dp))
                Text("Tap to dial when signal returns", color = SagipColors.TextDim, fontSize = 11.sp)
            }

            if (m.sources.isNotEmpty() && !m.streaming) {
                Spacer(Modifier.height(10.dp))
                Text("Source: " + m.sources.joinToString("; "), color = SagipColors.TextDim, fontSize = 11.sp)
            }
        }
    }
}

/**
 * Reusable tap-to-call button (SOP: every phone number/hotline is a dial button).
 * Fires ACTION_DIAL so no CALL_PHONE permission is needed — opens the dialer
 * pre-filled with [number] and the user taps call.
 */
@Composable
private fun CallButton(label: String, number: String, color: Color) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(color)
            .clickable {
                context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$number".toUri()))
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(Icons.Filled.Phone, contentDescription = "Call $label", tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SeverityBanner(sev: Severity) {
    val (color, label) = when (sev) {
        Severity.CRITICAL -> SagipColors.SeverityCritical to "LIFE-THREATENING — act now"
        Severity.URGENT -> SagipColors.SeverityUrgent to "Urgent — act quickly"
        else -> SagipColors.SeverityInfo to "Info"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
    listening: Boolean,
    onMic: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(22.dp)).background(SagipColors.SurfaceStrong)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    if (listening) "Listening…" else "Describe the emergency…",
                    color = if (listening) SagipColors.Accent else SagipColors.TextDim, fontSize = 15.sp,
                )
            }
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                textStyle = TextStyle(color = SagipColors.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(SagipColors.Accent), modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(8.dp))
        // Mic button — turns accent/active while listening. Tappable circle for a bigger target.
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(if (listening) SagipColors.Accent else Color.Transparent)
                .clickable(onClick = onMic),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = if (listening) "Stop voice input" else "Voice input",
                tint = if (listening) Color.White else SagipColors.TextDim,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(10.dp))   // breathing room between mic and send
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(if (enabled) SagipColors.Accent else SagipColors.AccentSoft)
                .clickable(enabled = enabled, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
