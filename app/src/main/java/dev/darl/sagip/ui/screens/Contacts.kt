package dev.darl.sagip.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.darl.sagip.data.ContactDirectory
import dev.darl.sagip.data.UserProfile
import dev.darl.sagip.ui.components.CallRow
import dev.darl.sagip.ui.components.LocalLang
import dev.darl.sagip.ui.components.PillChip
import dev.darl.sagip.ui.components.tr
import dev.darl.sagip.ui.theme.SagipColors
import dev.darl.sagip.ui.theme.Space

private val G = Space.gutter.dp

@Composable
fun ContactsScreen(directory: ContactDirectory, profile: UserProfile) {
    val lang = LocalLang.current
    var selected by remember(directory, profile) {
        mutableStateOf(directory.barangayFor(profile.home)?.name ?: directory.barangays.firstOrNull()?.name.orEmpty())
    }
    val barangay = directory.barangays.firstOrNull { it.name == selected }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = G, end = G, top = Space.md.dp, bottom = Space.xxl.dp),
        verticalArrangement = Arrangement.spacedBy(Space.md.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs.dp)) {
                Text(tr(lang, "Contacts", "Mga Contact"), style = MaterialTheme.typography.headlineLarge)
                Text(
                    tr(lang, "Tap a number to open your dialer.", "I-tap ang numero para buksan ang dialer."),
                    style = MaterialTheme.typography.bodyMedium, color = SagipColors.Muted,
                )
            }
        }
        item { Section(tr(lang, "Emergency", "Emergency")) }
        items(directory.national) { c -> CallRow(c.label(lang), c.number, c.note, emphasis = c.number == "911") }
        if (profile.hasEmergencyContact) {
            item { Section(tr(lang, "Your emergency contact", "Iyong emergency contact")) }
            item { CallRow(profile.emergencyContactName, profile.emergencyContactNumber) }
        }
        if (directory.cityContacts.isNotEmpty()) {
            item { Section(directory.city) }
            items(directory.cityContacts) { c -> CallRow(c.label(lang), c.number) }
        }
        item { Section(tr(lang, "Your barangay", "Iyong barangay")) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(directory.barangays.map { it.name }) { n -> PillChip(n, n == selected, { selected = n }) }
            }
        }
        if (barangay != null) {
            items(barangay.contacts) { c -> CallRow("${c.label(lang)} · ${barangay.name}", c.number) }
        }
        item {
            Text(
                tr(
                    lang,
                    "Numbers marked \"not added yet\" are placeholders until barangay contacts are filled in.",
                    "Ang mga \"wala pang numero\" ay placeholder hanggang maidagdag ang contact ng barangay.",
                ),
                style = MaterialTheme.typography.bodySmall, color = SagipColors.Muted,
            )
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.md.dp))
}

/** The always-reachable SOS sheet: 911 first, then the people closest to you. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosSheet(directory: ContactDirectory, profile: UserProfile, onDismiss: () -> Unit, onAllContacts: () -> Unit) {
    val lang = LocalLang.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SagipColors.Paper,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = G).padding(bottom = Space.xl.dp),
            verticalArrangement = Arrangement.spacedBy(Space.md.dp),
        ) {
            Text(tr(lang, "Emergency contacts", "Mga emergency contact"), style = MaterialTheme.typography.headlineMedium)
            Text(
                tr(lang, "Stay calm. Tap to call — it opens your dialer.", "Manatiling kalmado. I-tap para tumawag."),
                style = MaterialTheme.typography.bodyMedium, color = SagipColors.Muted,
            )
            Spacer(Modifier.height(Space.xs.dp))
            directory.national.forEach { c -> CallRow(c.label(lang), c.number, c.note, emphasis = c.number == "911") }
            if (profile.hasEmergencyContact) CallRow(profile.emergencyContactName, profile.emergencyContactNumber, tr(lang, "Your emergency contact", "Iyong emergency contact"))
            directory.barangayFor(profile.home)?.let { b ->
                b.contacts.firstOrNull()?.let { CallRow("${it.label(lang)} · ${b.name}", it.number) }
            }
            PillChip(tr(lang, "All contacts", "Lahat ng contact"), selected = false, onClick = onAllContacts)
        }
    }
}
