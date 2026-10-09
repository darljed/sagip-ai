package dev.darl.sagip.onboarding

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.LinearProgressIndicator
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
import dev.darl.sagip.ui.theme.SagipColors

@Composable
fun OnboardingScreen(state: OnboardingState, onAnswer: (String) -> Unit, onSkipAll: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.transcript.size) {
        if (state.transcript.isNotEmpty()) listState.animateScrollToItem(state.transcript.size - 1)
    }

    Box(Modifier.fillMaxSize().background(SagipColors.CanvasGradient)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            // Header + progress
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(SagipColors.OrbGradient))
                Spacer(Modifier.width(10.dp))
                Text("SAGIP · Setup", color = SagipColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (!state.done) {
                    Text("Skip", color = SagipColors.TextDim, fontSize = 13.sp,
                        modifier = Modifier.clickable { onSkipAll() })
                }
            }
            LinearProgressIndicator(
                progress = { (state.index.toFloat() / state.total).coerceIn(0f, 1f) },
                color = SagipColors.Accent,
                trackColor = SagipColors.SurfaceStrong,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.transcript) { line ->
                    when (line.role) {
                        OnbRole.SAGIP -> SagipLine(line.text)
                        OnbRole.USER -> UserLine(line.text)
                    }
                }
            }

            if (!state.done) {
                InputRow(
                    value = input, onValueChange = { input = it },
                    onSend = { onAnswer(input); input = "" },
                )
            }
        }
    }
}

@Composable
private fun SagipLine(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            Modifier.widthIn(max = 320.dp)
                .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                .background(SagipColors.Surface).padding(14.dp)
        ) { Text(text, color = SagipColors.Text, fontSize = 15.sp) }
    }
}

@Composable
private fun UserLine(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                .background(SagipColors.Accent).padding(horizontal = 16.dp, vertical = 12.dp)
        ) { Text(text, color = Color.White, fontSize = 15.sp) }
    }
}

@Composable
private fun InputRow(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(22.dp)).background(SagipColors.SurfaceStrong)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Type your answer…", color = SagipColors.TextDim, fontSize = 15.sp)
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                textStyle = TextStyle(color = SagipColors.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(SagipColors.Accent),
                keyboardActions = KeyboardActions(onDone = { onSend() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(SagipColors.Accent)
                .clickable { onSend() },
            contentAlignment = Alignment.Center,
        ) { Text("→", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}
