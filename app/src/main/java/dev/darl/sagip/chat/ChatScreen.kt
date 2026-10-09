package dev.darl.sagip.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.darl.sagip.ui.components.LocalLang
import dev.darl.sagip.ui.components.PillChip
import dev.darl.sagip.ui.components.SeverityTag
import dev.darl.sagip.ui.components.TypingBubble
import dev.darl.sagip.ui.components.dial
import dev.darl.sagip.ui.components.tr
import dev.darl.sagip.ui.theme.SagipColors
import dev.darl.sagip.ui.theme.Space
import dev.darl.sagip.data.Severity

private val G = Space.gutter.dp
private val BotShape = RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)
private val UserShape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)

/** Support-agent chat. Insets, the top bar (SOS) and the bottom bar are owned by the app shell. */
@Composable
fun ChatScreen(
    state: ChatState,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    listening: Boolean,
    quickAsks: List<String>,
    onQuickAsk: (String) -> Unit,
    onOpenTopic: (String) -> Unit,
    voiceHint: String? = null,
    onVoiceHintClick: (() -> Unit)? = null,
    onMic: () -> Unit,
) {
    val listState = rememberLazyListState()
    val lastLen = state.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(state.messages.size, lastLen / 40) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1, scrollOffset = 100_000)
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isEmpty()) {
                EmptyState(quickAsks, onQuickAsk)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = G, vertical = Space.lg.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.xl.dp),
                ) {
                    itemsIndexed(state.messages) { _, m ->
                        when (m.role) {
                            Role.USER -> UserBubble(m)
                            Role.ASSISTANT -> AssistantBubble(m, onOpenTopic)
                        }
                    }
                }
            }
        }
        voiceHint?.let {
            Text(
                it, color = SagipColors.SeverityUrgent, style = MaterialTheme.typography.labelMedium,
                fontWeight = if (onVoiceHintClick != null) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = G + 4.dp, vertical = 4.dp)
                    .then(if (onVoiceHintClick != null) Modifier.clickable { onVoiceHintClick() } else Modifier),
            )
        }
        InputBar(input, onInputChange, enabled = !state.busy, onSend = onSend, listening = listening, onMic = onMic)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(quickAsks: List<String>, onPick: (String) -> Unit) {
    val lang = LocalLang.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = G),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(tr(lang, "How can I help?", "Paano kita matutulungan?"), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.size(Space.sm.dp))
        Text(
            tr(lang, "Describe what's happening. I answer from the offline guides on this phone.", "Ikuwento ang nangyayari. Sasagot ako gamit ang mga offline na gabay sa phone na ito."),
            style = MaterialTheme.typography.bodyMedium, color = SagipColors.Muted,
        )
        Spacer(Modifier.size(Space.xl.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            quickAsks.forEach { q -> PillChip(q, false, { onPick(q) }) }
        }
    }
}

@Composable
private fun UserBubble(m: Message) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier.widthIn(max = 320.dp).clip(UserShape).background(SagipColors.Ink)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) { Text(m.text, style = MaterialTheme.typography.bodyMedium, color = Color.White) }
    }
}

@Composable
private fun AssistantBubble(m: Message, onOpenTopic: (String) -> Unit) {
    val lang = LocalLang.current
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.md.dp)) {
        if (m.streaming && m.text.isBlank()) {
            TypingBubble(tr(lang, "SAGIP is reading the guides…", "Binabasa ng SAGIP ang mga gabay…"))
            return@Column
        }
        Column(
            Modifier.fillMaxWidth().clip(BotShape).background(SagipColors.Card)
                .border(1.dp, SagipColors.Line, BotShape).padding(Space.lg.dp),
            verticalArrangement = Arrangement.spacedBy(Space.md.dp),
        ) {
            if (m.severity == Severity.CRITICAL || m.severity == Severity.URGENT) SeverityTag(m.severity)
            Text(renderInlineMarkdown(m.text), style = MaterialTheme.typography.bodyLarge)
            if (m.streaming) TypingDots()
        }
        if (m.contacts.isNotEmpty() && !m.streaming) {
            FlowRowChips {
                m.contacts.forEach { c ->
                    Row(
                        Modifier.heightIn(min = 48.dp).clip(CircleShape).border(1.dp, SagipColors.Ink, CircleShape)
                            .clickable { dial(ctx, c.number) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Call, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${tr(lang, "Call", "Tawagan")} ${c.label}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (m.related.isNotEmpty() && !m.streaming) {
            Text(tr(lang, "Related guides", "Mga kaugnay na gabay"), style = MaterialTheme.typography.labelMedium, color = SagipColors.Muted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(m.related) { g ->
                    Column(
                        Modifier.width(230.dp).clip(RoundedCornerShape(20.dp)).background(SagipColors.Card)
                            .border(1.dp, SagipColors.Line, RoundedCornerShape(20.dp))
                            .clickable { onOpenTopic(g.topicId) }.padding(Space.lg.dp),
                        verticalArrangement = Arrangement.spacedBy(Space.sm.dp),
                    ) {
                        SeverityTag(g.severity)
                        Text(g.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(tr(lang, "Open guide →", "Buksan ang gabay →"), style = MaterialTheme.typography.labelMedium, color = SagipColors.Blue)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

/** Tiny live-streaming marker under partial text. */
@Composable
private fun TypingDots() {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { Box(Modifier.size(6.dp).clip(CircleShape).background(SagipColors.Muted.copy(alpha = 0.5f))) }
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
    val lang = LocalLang.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = G, vertical = Space.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md.dp),
    ) {
        TextField(
            value = value, onValueChange = onValueChange, enabled = enabled,
            placeholder = { Text(tr(lang, "Describe your situation…", "Ikuwento ang nangyayari…"), color = SagipColors.Muted, style = MaterialTheme.typography.bodyMedium) },
            textStyle = MaterialTheme.typography.bodyMedium, maxLines = 4,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SagipColors.Card, unfocusedContainerColor = SagipColors.Card, disabledContainerColor = SagipColors.Card,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f).heightIn(min = 56.dp).border(1.dp, SagipColors.Line, RoundedCornerShape(28.dp)),
        )
        Box(
            Modifier.size(52.dp).clip(CircleShape)
                .background(if (listening) SagipColors.Coral else SagipColors.Card)
                .border(1.dp, if (listening) SagipColors.Coral else SagipColors.Line, CircleShape)
                .clickable(onClick = onMic)
                .semantics { contentDescription = tr(lang, "Voice input", "Boses") },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (listening) Icons.Outlined.Mic else Icons.Outlined.MicNone, null, tint = if (listening) Color.White else SagipColors.Ink)
        }
        Box(
            Modifier.size(52.dp).clip(CircleShape)
                .background(if (enabled && value.isNotBlank()) SagipColors.Ink else SagipColors.Line)
                .clickable(enabled = enabled && value.isNotBlank(), onClick = onSend)
                .semantics { contentDescription = tr(lang, "Send", "Ipadala") },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Outlined.Send, null, tint = if (enabled && value.isNotBlank()) SagipColors.Acid else Color.White) }
    }
}
