package dev.darl.sagip.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.darl.sagip.data.Category
import dev.darl.sagip.data.Illustrations
import dev.darl.sagip.data.Lang
import dev.darl.sagip.data.Severity
import dev.darl.sagip.data.Topic
import dev.darl.sagip.ui.theme.SagipColors
import dev.darl.sagip.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalIllustrations = staticCompositionLocalOf<Illustrations?> { null }
val LocalLang = staticCompositionLocalOf { Lang.TL }

/** Opens the dialer (ACTION_DIAL needs no CALL_PHONE permission). */
fun dial(context: Context, number: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$number".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun tr(lang: Lang, en: String, tl: String) = if (lang == Lang.TL) tl else en

// ───────────────────────── Images ─────────────────────────

/**
 * An illustration slot. Shows the real image from assets when [path] resolves, otherwise a
 * tinted placeholder (so layout is final before the art is). [label] adds a small
 * "image slot" caption on placeholders; it disappears automatically once art exists.
 */
@Composable
fun ImageSlot(
    path: String?,
    tint: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    label: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    description: String? = null,
) {
    val illus = LocalIllustrations.current
    var bmp by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path, illus) {
        bmp = if (path != null && illus != null) withContext(Dispatchers.IO) { illus.load(path)?.asImageBitmap() } else null
    }
    val a11y = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    Box(modifier.then(a11y).background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.55f))))) {
        val b = bmp
        if (b != null) {
            Image(b, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = contentScale)
        } else {
            Icon(icon, null, tint = SagipColors.Ink.copy(alpha = 0.22f), modifier = Modifier.size(44.dp).align(Alignment.Center))
            if (label != null) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(Space.md.dp)
                        .clip(CircleShape).background(Color.White.copy(alpha = 0.75f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Image, null, tint = SagipColors.Muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = SagipColors.Muted)
                }
            }
        }
    }
}

// ───────────────────────── Chips / tags ─────────────────────────

@Composable
fun PillChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(if (selected) SagipColors.Ink else SagipColors.Card)
            .border(BorderStroke(1.dp, if (selected) SagipColors.Ink else SagipColors.Line), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else SagipColors.Ink, maxLines = 1,
        )
    }
}

fun severityColor(s: Severity): Color = when (s) {
    Severity.INFO -> SagipColors.SeverityInfo
    Severity.CAUTION -> SagipColors.SeverityCaution
    Severity.URGENT -> SagipColors.SeverityUrgent
    Severity.CRITICAL -> SagipColors.SeverityCritical
}

fun severityLabel(s: Severity, lang: Lang): String = when (s) {
    Severity.INFO -> tr(lang, "Good to know", "Alamin")
    Severity.CAUTION -> tr(lang, "Be careful", "Mag-ingat")
    Severity.URGENT -> tr(lang, "Act soon", "Kumilos agad")
    Severity.CRITICAL -> tr(lang, "Life-threatening", "Nakamamatay")
}

@Composable
fun SeverityTag(severity: Severity, modifier: Modifier = Modifier) {
    val c = severityColor(severity)
    Row(
        modifier.clip(CircleShape).background(c.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Spacer(Modifier.size(8.dp))
        Text(severityLabel(severity, LocalLang.current), style = MaterialTheme.typography.labelMedium, color = c, fontWeight = FontWeight.SemiBold)
    }
}

// ───────────────────────── Cards ─────────────────────────

private val CardShape = RoundedCornerShape(22.dp)

/** Stable per-id height variation so the staggered grid looks editorial, not uniform. */
fun staggerHeight(seed: String, base: Int = 120, step: Int = 28, steps: Int = 4): Int =
    base + (seed.hashCode().let { if (it < 0) -it else it } % steps) * step

@Composable
fun CategoryCard(category: Category, count: Int, coverPath: String?, imageHeight: Int, onClick: () -> Unit) {
    val lang = LocalLang.current
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(SagipColors.Card)
            .border(1.dp, SagipColors.Line, CardShape).clickable(onClick = onClick),
    ) {
        ImageSlot(coverPath, category.tint, category.icon, Modifier.fillMaxWidth().height(imageHeight.dp))
        Column(Modifier.padding(Space.lg.dp)) {
            Text(category.title(lang), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Space.xs.dp))
            Text(
                tr(lang, "$count guides", "$count gabay"),
                style = MaterialTheme.typography.labelMedium, color = SagipColors.Muted,
            )
        }
    }
}

@Composable
fun TopicCard(topic: Topic, category: Category, heroPath: String?, imageHeight: Int, onClick: () -> Unit) {
    val lang = LocalLang.current
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(SagipColors.Card)
            .border(1.dp, SagipColors.Line, CardShape).clickable(onClick = onClick),
    ) {
        // Only a few guides will ever get art, so no empty image boxes: guides without a hero
        // get a compact icon badge instead and the grid stays dense.
        if (heroPath != null) {
            ImageSlot(heroPath, category.tint, category.icon, Modifier.fillMaxWidth().height(imageHeight.dp))
        }
        Column(Modifier.padding(Space.lg.dp), verticalArrangement = Arrangement.spacedBy(Space.sm.dp)) {
            if (heroPath == null) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(category.tint), contentAlignment = Alignment.Center) {
                    Icon(category.icon, null, tint = SagipColors.Ink.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                }
            }
            SeverityTag(topic.severity)
            Text(topic.title(lang), style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val sum = topic.summary(lang)
            if (sum.isNotBlank()) {
                Text(sum, style = MaterialTheme.typography.bodySmall, color = SagipColors.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ───────────────────────── Buttons ─────────────────────────

/** The single loud action: SOS. Always reachable from the top bar. */
@Composable
fun SosPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = 44.dp).clip(CircleShape).background(SagipColors.Coral)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp)
            .semantics { contentDescription = "SOS emergency contacts" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Call, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text("SOS", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/** Tap-to-call row. A null [number] renders a muted "not added yet" placeholder. */
@Composable
fun CallRow(label: String, number: String?, note: String = "", emphasis: Boolean = false, modifier: Modifier = Modifier, sample: Boolean = false) {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    val enabled = number != null
    val bg = if (emphasis && enabled) SagipColors.Coral else SagipColors.Card
    val fg = if (emphasis && enabled) Color.White else SagipColors.Ink
    Row(
        modifier.fillMaxWidth().heightIn(min = 68.dp).clip(RoundedCornerShape(20.dp)).background(bg)
            .border(1.dp, if (emphasis && enabled) bg else SagipColors.Line, RoundedCornerShape(20.dp))
            .then(if (enabled) Modifier.clickable { dial(ctx, number!!) } else Modifier)
            .padding(horizontal = Space.lg.dp, vertical = Space.md.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = fg, modifier = Modifier.weight(1f, fill = false))
                if (sample) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        tr(lang, "Sample", "Sample"), style = MaterialTheme.typography.labelSmall, color = SagipColors.Muted,
                        modifier = Modifier.clip(CircleShape).background(SagipColors.SurfaceStrong).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            val sub = number ?: tr(lang, "Number not added yet", "Wala pang numero")
            Text(
                if (note.isBlank()) sub else "$sub · $note",
                style = MaterialTheme.typography.bodySmall,
                color = if (emphasis && enabled) Color.White.copy(alpha = 0.85f) else SagipColors.Muted,
            )
        }
        if (enabled) {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (emphasis) Color.White.copy(alpha = 0.22f) else SagipColors.Ink),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Call, tint = Color.White, contentDescription = tr(lang, "Call", "Tawag"), modifier = Modifier.size(22.dp)) }
        }
    }
}

// ───────────────────────── Chat bits ─────────────────────────

/** Animated three-dot "SAGIP is thinking" bubble, with the label beside it. */
@Composable
fun TypingBubble(label: String, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "typing")
    Row(
        modifier.clip(RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)).background(SagipColors.Card)
            .border(1.dp, SagipColors.Line, RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val p by t.animateFloat(
                0.35f, 1f,
                infiniteRepeatable(tween(520, delayMillis = i * 160), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.size(10.dp).scale(0.7f + 0.5f * p).alpha(p).clip(CircleShape).background(SagipColors.Ink))
            Spacer(Modifier.size(7.dp))
        }
        Spacer(Modifier.size(Space.sm.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = SagipColors.Muted)
    }
}
