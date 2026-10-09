package dev.darl.sagip.onboarding

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.darl.sagip.data.Lang
import dev.darl.sagip.ui.theme.SagipColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardScreen(
    state: WizardState,
    onValue: (String) -> Unit,
    onToggleChip: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onPickContact: () -> Unit,
    onPickDate: () -> Unit,
) {
    val step = state.step
    val lang = state.lang

    Box(Modifier.fillMaxSize().background(SagipColors.CanvasGradient)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {

            // Header + progress tabs
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(SagipColors.OrbGradient))
                Spacer(Modifier.width(10.dp))
                Text("Set up SAGIP", color = SagipColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${state.index + 1} / ${state.total}", color = SagipColors.TextDim, fontSize = 13.sp)
            }
            // Segmented progress tabs
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(state.total) { i ->
                    Box(
                        Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (i <= state.index) SagipColors.Accent else SagipColors.SurfaceStrong)
                    )
                }
            }

            // Question body (scrollable)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
            ) {
                Row {
                    Text(step.title(lang), color = SagipColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
                if (!step.required) {
                    Spacer(Modifier.height(6.dp))
                    Text(if (lang == Lang.TL) "Opsyonal" else "Optional", color = SagipColors.TextDim, fontSize = 13.sp)
                }
                Spacer(Modifier.height(24.dp))

                when (step.kind) {
                    StepKind.CHIPS_SINGLE -> ChipGroup(step.chips(lang), state.value, multi = false, onToggleChip)
                    StepKind.CHIPS_MULTI -> ChipGroup(step.chips(lang), state.value, multi = true, onToggleChip)
                    StepKind.DATE -> DateField(state.value, lang, onPickDate)
                    StepKind.PHONE -> PhoneField(state.value, lang, onValue, onPickContact)
                    StepKind.TEXT -> TextField(state.value, lang, onValue, number = false)
                    StepKind.YESNO -> ChipGroup(
                        if (lang == Lang.TL) listOf("Oo", "Hindi") else listOf("Yes", "No"),
                        state.value, multi = false, onToggleChip,
                    )
                }

                if (state.showRequiredError) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (lang == Lang.TL) "Kailangan ito para magpatuloy." else "This is required to continue.",
                        color = SagipColors.SeverityUrgent, fontSize = 13.sp,
                    )
                }
            }

            // Nav row: Back | Skip | Next
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.index > 0) {
                    PillButton(if (lang == Lang.TL) "Bumalik" else "Back", filled = false, onClick = onBack)
                }
                Spacer(Modifier.weight(1f))
                if (!step.required) {
                    Text(if (lang == Lang.TL) "Laktawan" else "Skip",
                        color = SagipColors.TextDim, fontSize = 14.sp,
                        modifier = Modifier.clickable { onSkip() }.padding(horizontal = 16.dp, vertical = 10.dp))
                }
                PillButton(
                    if (state.index == state.total - 1) (if (lang == Lang.TL) "Tapos" else "Finish")
                    else (if (lang == Lang.TL) "Susunod" else "Next"),
                    filled = true, onClick = onNext,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(chips: List<String>, value: String, multi: Boolean, onToggle: (String, Boolean) -> Unit) {
    val selected = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        chips.forEach { chip ->
            val isSel = selected.any { it.equals(chip, true) }
            Box(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(if (isSel) SagipColors.Accent else SagipColors.AccentSoft)
                    .clickable { onToggle(chip, multi) }
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(chip, color = if (isSel) Color.White else SagipColors.Text, fontSize = 15.sp,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun TextField(value: String, lang: Lang, onValue: (String) -> Unit, number: Boolean) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SagipColors.SurfaceStrong)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        if (value.isEmpty()) Text(if (lang == Lang.TL) "I-type dito…" else "Type here…", color = SagipColors.TextDim, fontSize = 16.sp)
        BasicTextField(
            value = value, onValueChange = onValue, singleLine = true,
            textStyle = TextStyle(color = SagipColors.Text, fontSize = 16.sp),
            cursorBrush = SolidColor(SagipColors.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Phone else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PhoneField(value: String, lang: Lang, onValue: (String) -> Unit, onPickContact: () -> Unit) {
    Column {
        TextField(value, lang, onValue, number = true)
        Spacer(Modifier.height(12.dp))
        PillButton(if (lang == Lang.TL) "Pumili mula sa Contacts" else "Pick from Contacts", filled = false, onClick = onPickContact)
    }
}

@Composable
private fun DateField(value: String, lang: Lang, onPickDate: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SagipColors.SurfaceStrong)
            .clickable { onPickDate() }.padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(
            value.ifEmpty { if (lang == Lang.TL) "Pumili ng petsa" else "Pick a date" },
            color = if (value.isEmpty()) SagipColors.TextDim else SagipColors.Text, fontSize = 16.sp,
        )
    }
}

@Composable
private fun PillButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (filled) SagipColors.Accent else SagipColors.SurfaceStrong)
            .clickable { onClick() }.padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(label, color = if (filled) Color.White else SagipColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
