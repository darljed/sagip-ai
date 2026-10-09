package dev.darl.sagip.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.darl.sagip.R
import dev.darl.sagip.ui.components.LocalLang
import dev.darl.sagip.ui.components.SosPill
import dev.darl.sagip.ui.components.tr
import dev.darl.sagip.ui.theme.SagipColors
import dev.darl.sagip.ui.theme.Space

/**
 * Shown while Gemma 4 loads (a few seconds): logo, what S-A-G-I-P stands for, the mascot.
 * SOS stays reachable, and after a few seconds the user can skip into the guides.
 */
@Composable
fun LoadingScreen(
    modelName: String,
    version: String,
    demo: Boolean,
    onSos: () -> Unit,
    onSkip: () -> Unit,
    onDismissDemo: () -> Unit,
) {
    val lang = LocalLang.current
    var canSkip by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(7000); canSkip = true }


    Column(
        Modifier.fillMaxSize().background(SagipColors.Paper)
            .then(if (demo) Modifier.clickable(onClick = onDismissDemo) else Modifier)
            .statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = Space.gutter.dp, vertical = Space.md.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SosPill(onSos) }
        Spacer(Modifier.weight(0.6f))
        Image(painterResource(R.drawable.sagip_logo), null, Modifier.size(120.dp).clip(RoundedCornerShape(30.dp)))
        Spacer(Modifier.height(Space.lg.dp))
        Text("SAGIP", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(Space.sm.dp))
        // S-mart A-id & G-uidance for I-mmediate P-reparedness (initials highlighted)
        val initial = SpanStyle(color = SagipColors.Coral, fontWeight = FontWeight.Bold)
        Text(
            buildAnnotatedString {
                withStyle(initial) { append("S") }; append("mart ")
                withStyle(initial) { append("A") }; append("id & ")
                withStyle(initial) { append("G") }; append("uidance for ")
                withStyle(initial) { append("I") }; append("mmediate ")
                withStyle(initial) { append("P") }; append("reparedness")
            },
            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
        )
        Text(
            tr(lang, "Your offline guide for emergencies and survival.", "Matalinong Tulong at Gabay para sa Agarang Paghahanda"),
            style = MaterialTheme.typography.bodyMedium, color = SagipColors.Muted, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Space.xs.dp),
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(Space.lg.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(0.7f).height(6.dp).clip(CircleShape),
            color = SagipColors.Ink, trackColor = SagipColors.Line,
        )
        Spacer(Modifier.height(Space.md.dp))
        Text(
            tr(lang, "Loading $modelName — runs on this phone, in offline mode.", "Nilo-load ang $modelName — tumatakbo sa phone na ito, sa offline mode."),
            style = MaterialTheme.typography.labelMedium, color = SagipColors.Muted, textAlign = TextAlign.Center,
        )
        Text(version, style = MaterialTheme.typography.labelSmall, color = SagipColors.Muted, modifier = Modifier.padding(top = Space.sm.dp))
        Box(Modifier.heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
            if (!demo && canSkip) {
                Text(
                    tr(lang, "Skip — browse guides while it loads", "Laktawan — mag-browse ng gabay habang naglo-load"),
                    style = MaterialTheme.typography.labelLarge, color = SagipColors.Blue,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onSkip).heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}
