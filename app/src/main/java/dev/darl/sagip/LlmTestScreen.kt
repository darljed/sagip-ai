package dev.darl.sagip

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.darl.sagip.llm.LlmEngine
import dev.darl.sagip.ui.theme.SagipColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 1 proof-of-life screen: type a prompt, get a streamed on-device response.
 * Deliberately throwaway UI — the real chat lands in later phases. This exists to
 * prove the model loads and generates in AIRPLANE MODE.
 */
@Composable
fun LlmTestScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var prompt by remember { mutableStateOf("What are 3 steps for severe bleeding?") }
    var output by remember { mutableStateOf("") }
    var status by remember {
        mutableStateOf(
            if (LlmEngine.modelExists()) "Model found · tap send"
            else "⚠ Model not on device yet (push the .task to /data/local/tmp/llm/)"
        )
    }
    var busy by remember { mutableStateOf(false) }
    var engine by remember { mutableStateOf<LlmEngine?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SagipColors.CanvasGradient)
            .padding(horizontal = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 56.dp, bottom = 24.dp)) {
            // Header with Offline·Ready dot
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(SagipColors.OrbGradient)
                )
                Spacer(Modifier.width(10.dp))
                Text("SAGIP", color = SagipColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(8.dp).clip(CircleShape).background(SagipColors.Ok))
                Spacer(Modifier.width(6.dp))
                Text("Offline · Ready", color = SagipColors.TextDim, fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))
            Text(status, color = SagipColors.TextDim, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))

            // Output area (assistant card)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                    .background(SagipColors.Surface)
                    .padding(16.dp)
            ) {
                Text(
                    text = output.ifEmpty { "Response will stream here…" },
                    color = if (output.isEmpty()) SagipColors.TextDim else SagipColors.Text,
                    fontSize = 15.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }

            Spacer(Modifier.height(14.dp))

            // Input pill + send
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(SagipColors.SurfaceStrong)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    singleLine = true,
                    textStyle = TextStyle(color = SagipColors.Text, fontSize = 15.sp),
                    cursorBrush = SolidColor(SagipColors.Accent),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (busy) SagipColors.AccentSoft else SagipColors.Accent)
                        .clickableIf(!busy) {
                            runGeneration(
                                scope, context, prompt,
                                getEngine = { engine },
                                setEngine = { engine = it },
                                onStatus = { status = it },
                                onOutput = { output = it },
                                onBusy = { busy = it },
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (busy) "…" else "↑", color = SagipColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Tiny helper so we don't pull in extra deps for a conditional click. */
private fun Modifier.clickableIf(enabled: Boolean, onClick: () -> Unit): Modifier =
    if (enabled) this.clickable(onClick = onClick) else this

private fun runGeneration(
    scope: CoroutineScope,
    context: android.content.Context,
    prompt: String,
    getEngine: () -> LlmEngine?,
    setEngine: (LlmEngine) -> Unit,
    onStatus: (String) -> Unit,
    onOutput: (String) -> Unit,
    onBusy: (Boolean) -> Unit,
) {
    scope.launch {
        onBusy(true)
        onOutput("")
        try {
            if (!LlmEngine.modelExists()) {
                onStatus("⚠ Model missing at ${LlmEngine.DEFAULT_MODEL_PATH}")
                onBusy(false)
                return@launch
            }
            onStatus("Loading model… (first load can take ~10–30s)")
            val sb = StringBuilder()
            // Create the engine once; reuse across prompts.
            val eng = getEngine() ?: withContext(Dispatchers.IO) {
                LlmEngine.create(context = context)
            }.also { setEngine(it) }

            onStatus("Generating on-device…")
            // Partial tokens arrive on a MediaPipe worker thread — marshal to main.
            withContext(Dispatchers.IO) {
                eng.generateAsync(prompt) { partial, done ->
                    sb.append(partial)
                    val snapshot = sb.toString()
                    scope.launch(Dispatchers.Main) {
                        onOutput(snapshot)
                        if (done) {
                            onStatus("Done · generated on-device, offline")
                            onBusy(false)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            onStatus("Error: ${t.message}")
            onBusy(false)
        }
    }
}
