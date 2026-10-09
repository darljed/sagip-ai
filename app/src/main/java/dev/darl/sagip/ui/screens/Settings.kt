package dev.darl.sagip.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.darl.sagip.R
import dev.darl.sagip.data.Lang
import dev.darl.sagip.data.UserProfile
import dev.darl.sagip.ui.components.LocalLang
import dev.darl.sagip.ui.components.PillChip
import dev.darl.sagip.ui.components.tr
import dev.darl.sagip.ui.theme.SagipColors
import dev.darl.sagip.ui.theme.Space
import dev.darl.sagip.ui.theme.ThemeMode

private val G = Space.gutter.dp

private fun csv(s: String) = s.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }

/** Settings: language, theme, profile details, reset chats, about. Language/theme apply instantly; details on Save. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    profile: UserProfile,
    themeMode: ThemeMode,
    modelLine: String,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onSave: (UserProfile) -> Unit,
    onResetChats: () -> Unit,
    onPickContact: ((String, String) -> Unit) -> Unit,
    onPickDate: (String, (String) -> Unit) -> Unit,
    onLocate: ((String) -> Unit) -> Unit,
) {
    val lang = LocalLang.current
    var draft by remember(profile) { mutableStateOf(profile) }
    // Text fields for comma lists keep their own raw text so typing "a, " isn't mangled.
    var allergies by remember(profile) { mutableStateOf(profile.allergies.joinToString(", ")) }
    var conditions by remember(profile) { mutableStateOf(profile.conditions.joinToString(", ")) }
    var meds by remember(profile) { mutableStateOf(profile.medications.joinToString(", ")) }
    var confirmReset by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    val edited = draft.copy(allergies = csv(allergies), conditions = csv(conditions), medications = csv(meds))
    val dirty = edited != profile
    val contactOk = edited.emergencyContactNumber.isBlank() || validPhone(edited.emergencyContactNumber)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Space.huge.dp),
        verticalArrangement = Arrangement.spacedBy(Space.md.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr(lang, "Back", "Bumalik"), tint = SagipColors.Ink)
                }
                Text(tr(lang, "Settings", "Mga Setting"), style = MaterialTheme.typography.headlineMedium)
            }
        }

        item { Group(tr(lang, "Language", "Wika")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillChip("English", profile.preferredLanguage == Lang.EN, { onSave(profile.copy(preferredLanguage = Lang.EN)) })
                PillChip("Tagalog", profile.preferredLanguage == Lang.TL, { onSave(profile.copy(preferredLanguage = Lang.TL)) })
            }
            Hint(tr(lang, "Guides, answers and menus follow this language.", "Susunod dito ang mga gabay, sagot at menu."))
        } }

        item { Group(tr(lang, "Appearance", "Itsura")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillChip(tr(lang, "System", "Ayon sa phone"), themeMode == ThemeMode.SYSTEM, { onTheme(ThemeMode.SYSTEM) })
                PillChip(tr(lang, "Light", "Maliwanag"), themeMode == ThemeMode.LIGHT, { onTheme(ThemeMode.LIGHT) })
                PillChip(tr(lang, "Dark", "Madilim"), themeMode == ThemeMode.DARK, { onTheme(ThemeMode.DARK) })
            }
        } }

        item { Group(tr(lang, "About you", "Tungkol sa iyo")) {
            Field(draft.name, { draft = draft.copy(name = it); saved = false }, tr(lang, "Name", "Pangalan"))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field(draft.birthday, { draft = draft.copy(birthday = it); saved = false }, tr(lang, "Birthday (yyyy-mm-dd)", "Kaarawan (yyyy-mm-dd)")) }
                PillChip(tr(lang, "Pick", "Pumili"), false, { onPickDate(draft.birthday) { draft = draft.copy(birthday = it); saved = false } })
            }
            Field(draft.bloodType, { draft = draft.copy(bloodType = it); saved = false }, tr(lang, "Blood type (e.g. O+)", "Blood type (hal. O+)"))
        } }

        item { Group(tr(lang, "Health notes", "Tala sa kalusugan")) {
            Field(allergies, { allergies = it; saved = false }, tr(lang, "Allergies (comma separated)", "Allergy (pinaghihiwalay ng kuwit)"))
            Field(conditions, { conditions = it; saved = false }, tr(lang, "Conditions", "Kondisyon"))
            Field(meds, { meds = it; saved = false }, tr(lang, "Medications", "Gamot"))
            Hint(tr(lang, "Stays on this phone. Used only to personalise guidance.", "Nasa phone lang ito. Ginagamit lang para i-personalize ang gabay."))
        } }

        item { Group(tr(lang, "Where you live", "Tirahan")) {
            Field(draft.home, { draft = draft.copy(home = it); saved = false }, tr(lang, "Barangay / city", "Barangay / lungsod"))
            PillChip(tr(lang, "Use my location", "Gamitin ang lokasyon ko"), false, { onLocate { raw -> dev.darl.sagip.data.placeLabel(raw).let { if (it.isNotBlank()) { draft = draft.copy(home = it); saved = false } } } })
        } }

        item { Group(tr(lang, "Household", "Sambahayan")) {
            ToggleRow(tr(lang, "Infant at home", "May sanggol"), draft.householdInfant) { draft = draft.copy(householdInfant = it); saved = false }
            ToggleRow(tr(lang, "Elderly at home", "May nakatatanda"), draft.householdElderly) { draft = draft.copy(householdElderly = it); saved = false }
            ToggleRow(tr(lang, "Person with disability", "May PWD"), draft.householdPwd) { draft = draft.copy(householdPwd = it); saved = false }
            ToggleRow(tr(lang, "Pregnant", "Buntis"), draft.householdPregnant) { draft = draft.copy(householdPregnant = it); saved = false }
        } }

        item { Group(tr(lang, "Emergency contact", "Emergency contact")) {
            Field(draft.emergencyContactName, { draft = draft.copy(emergencyContactName = it); saved = false }, tr(lang, "Name (e.g. Mama)", "Pangalan (hal. Mama)"))
            Field(draft.emergencyContactNumber, { draft = draft.copy(emergencyContactNumber = it); saved = false }, tr(lang, "Phone number", "Numero"), phone = true, error = !contactOk)
            PillChip(tr(lang, "Pick from phone contacts", "Pumili sa contacts ng phone"), false, { onPickContact { n, num -> draft = draft.copy(emergencyContactName = n, emergencyContactNumber = num); saved = false } })
        } }

        item {
            Column(Modifier.padding(horizontal = G), verticalArrangement = Arrangement.spacedBy(Space.sm.dp)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CircleShape)
                        .background(if (dirty && contactOk) SagipColors.Ink else SagipColors.Line)
                        .clickable(enabled = dirty && contactOk) {
                            onSave(edited.copy(emergencyContactName = edited.emergencyContactName.trim(), emergencyContactNumber = edited.emergencyContactNumber.trim()))
                            saved = true
                        },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (saved && !dirty) tr(lang, "Saved ✓", "Na-save ✓") else tr(lang, "Save changes", "I-save ang mga pagbabago"),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (dirty && contactOk) SagipColors.OnInk else SagipColors.Muted,
                    )
                }
            }
        }

        item { Group(tr(lang, "Reset", "I-reset")) {
            Text(
                tr(lang,
                    "Clears every conversation and the chat history on this phone. Your details, language, theme, contacts and the app's guides stay.",
                    "Buburahin ang lahat ng usapan at kasaysayan ng chat sa phone na ito. Mananatili ang iyong detalye, wika, theme, contact at mga gabay ng app."),
                style = MaterialTheme.typography.bodySmall, color = SagipColors.Muted,
            )
            Box(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(CircleShape)
                    .border(1.dp, SagipColors.SeverityCritical, CircleShape).clickable { confirmReset = true },
                contentAlignment = Alignment.Center,
            ) { Text(tr(lang, "Reset chats & sessions", "I-reset ang mga chat at session"), style = MaterialTheme.typography.labelLarge, color = SagipColors.SeverityCritical) }
        } }

        item { AboutCard(modelLine) }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = SagipColors.Paper,
            title = { Text(tr(lang, "Reset chats & sessions?", "I-reset ang mga chat at session?"), style = MaterialTheme.typography.titleLarge) },
            text = { Text(tr(lang, "All conversations and history will be deleted. This can't be undone.", "Mabubura ang lahat ng usapan at kasaysayan. Hindi na ito maibabalik.")) },
            confirmButton = { TextButton(onClick = { onResetChats(); confirmReset = false }) { Text(tr(lang, "Reset", "I-reset"), color = SagipColors.SeverityCritical) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(tr(lang, "Cancel", "Kanselahin")) } },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = G).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SagipColors.Card)
            .border(1.dp, SagipColors.Line, RoundedCornerShape(22.dp)).padding(Space.lg.dp),
        verticalArrangement = Arrangement.spacedBy(Space.md.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = SagipColors.Muted)

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, phone: Boolean = false, error: Boolean = false) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, isError = error,
        textStyle = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = if (phone) KeyboardOptions(keyboardType = KeyboardType.Phone) else KeyboardOptions.Default,
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = SagipColors.Ink, checkedThumbColor = SagipColors.OnInk),
        )
    }
}

@Composable
private fun AboutCard(modelLine: String) {
    val lang = LocalLang.current
    Column(
        Modifier.padding(horizontal = G).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SagipColors.Card)
            .border(1.dp, SagipColors.Line, RoundedCornerShape(22.dp)).padding(Space.lg.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.sm.dp),
    ) {
        Image(painterResource(R.drawable.sagip_mascot), null, Modifier.height(150.dp), contentScale = ContentScale.Fit)
        Text("SAGIP", style = MaterialTheme.typography.headlineSmall)
        Text(
            tr(lang, "Smart Aid & Guidance for Immediate Preparedness", "Matalinong Tulong at Gabay para sa Agarang Paghahanda"),
            style = MaterialTheme.typography.bodyMedium, color = SagipColors.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(Space.xs.dp))
        Text(modelLine, style = MaterialTheme.typography.labelSmall, color = SagipColors.Muted)
    }
}
