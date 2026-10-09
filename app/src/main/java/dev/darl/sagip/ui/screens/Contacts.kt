package dev.darl.sagip.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.darl.sagip.data.Area
import dev.darl.sagip.data.ContactDirectory
import dev.darl.sagip.data.Place
import dev.darl.sagip.data.UserProfile
import dev.darl.sagip.ui.components.CallRow
import dev.darl.sagip.ui.components.LocalLang
import dev.darl.sagip.ui.components.PillChip
import dev.darl.sagip.ui.components.tr
import dev.darl.sagip.ui.theme.SagipColors
import dev.darl.sagip.ui.theme.Space

private val G = Space.gutter.dp

/** Digits, +, spaces, dashes, parentheses only; at least 3 digits. */
fun validPhone(n: String) = n.matches(Regex("[+0-9()\\-\\s]+")) && n.count { it.isDigit() } >= 3

@Composable
fun ContactsScreen(
    directory: ContactDirectory,
    profile: UserProfile,
    placeText: String,
    onSaveEmergencyContact: (name: String, number: String) -> Unit,
    onPickPhoneContact: ((String, String) -> Unit) -> Unit,
) {
    val lang = LocalLang.current
    val detected: Place? = directory.placeFor(placeText) ?: directory.placeFor(profile.home)
    var chosen by remember(detected) { mutableStateOf<Area?>(null) }
    var editing by remember { mutableStateOf(false) }
    val area = chosen ?: detected?.area
    val local = if (chosen != null) chosen!!.contacts.filter { it.number != null }
                else directory.localContacts(detected)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = G, end = G, top = Space.md.dp, bottom = Space.xxl.dp),
        verticalArrangement = Arrangement.spacedBy(Space.md.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs.dp)) {
                Text(tr(lang, "Contacts", "Mga Contact"), style = MaterialTheme.typography.headlineLarge)
                Text(tr(lang, "Tap a number to open your dialer.", "I-tap ang numero para buksan ang dialer."), style = MaterialTheme.typography.bodyMedium, color = SagipColors.Muted)
            }
        }
        item { Section(tr(lang, "Emergency", "Emergency")) }
        items(directory.national) { c -> CallRow(c.label(lang), c.number, c.note, emphasis = c.number == "911") }

        item { Section(tr(lang, "Your emergency contact", "Iyong emergency contact")) }
        item {
            if (profile.hasEmergencyContact) CallRow(profile.emergencyContactName, profile.emergencyContactNumber, tr(lang, "Say “call my mom / wife” in Ask", "Sabihin sa Tanong: “tawagan si mama / asawa”"))
            else Text(tr(lang, "None saved yet.", "Wala pang naka-save."), style = MaterialTheme.typography.bodyMedium, color = SagipColors.Muted)
        }
        item { PillChip(if (profile.hasEmergencyContact) tr(lang, "Edit", "I-edit") else tr(lang, "Add emergency contact", "Magdagdag ng emergency contact"), false, { editing = true }) }

        item {
            Section(
                if (area != null) tr(lang, "Near you · ${(if (chosen == null) detected?.title else null) ?: area.name}", "Malapit sa iyo · ${(if (chosen == null) detected?.title else null) ?: area.name}")
                else tr(lang, "Near you", "Malapit sa iyo"),
            )
        }
        if (local.isEmpty()) {
            item {
                Text(
                    tr(lang, "No local numbers for your area yet. Pick another area below, or use 911.", "Wala pang lokal na numero para sa lugar mo. Pumili ng ibang lugar sa ibaba, o tumawag sa 911."),
                    style = MaterialTheme.typography.bodyMedium, color = SagipColors.Muted,
                )
            }
        } else {
            items(local) { c -> CallRow(c.label(lang), c.number, c.note, sample = c.sample) }
        }
        if (directory.areas.size > 1) {
            item { Text(tr(lang, "Other areas", "Iba pang lugar"), style = MaterialTheme.typography.titleSmall, color = SagipColors.Muted) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(directory.areas) { a -> PillChip(a.name, a == area, { chosen = if (a == detected?.area) null else a }) }
                }
            }
        }
    }

    if (editing) EditContactDialog(profile, onDismiss = { editing = false }, onPick = onPickPhoneContact) { n, num ->
        onSaveEmergencyContact(n, num); editing = false
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.md.dp))
}

@Composable
private fun EditContactDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onPick: ((String, String) -> Unit) -> Unit,
    onSave: (String, String) -> Unit,
) {
    val lang = LocalLang.current
    var name by remember { mutableStateOf(profile.emergencyContactName) }
    var number by remember { mutableStateOf(profile.emergencyContactNumber) }
    val ok = name.isNotBlank() && validPhone(number)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SagipColors.Paper,
        title = { Text(tr(lang, "Emergency contact", "Emergency contact"), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md.dp)) {
                Text(tr(lang, "This is who “call my mom / wife” will dial.", "Ito ang tatawagan kapag sinabi mong “tawagan si mama / asawa”."), style = MaterialTheme.typography.bodySmall, color = SagipColors.Muted)
                OutlinedTextField(name, { name = it }, label = { Text(tr(lang, "Name (e.g. Mama)", "Pangalan (hal. Mama)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    number, { number = it }, label = { Text(tr(lang, "Phone number", "Numero")) }, singleLine = true,
                    isError = number.isNotBlank() && !validPhone(number),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                )
                PillChip(tr(lang, "Pick from phone contacts", "Pumili sa contacts ng phone"), false, { onPick { n, num -> name = n; number = num } })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), number.trim()) }, enabled = ok) { Text(tr(lang, "Save", "I-save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(lang, "Cancel", "Kanselahin")) } },
    )
}

/** The always-reachable SOS sheet: 911 first, then the people closest to you. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosSheet(directory: ContactDirectory, profile: UserProfile, placeText: String, onDismiss: () -> Unit, onAllContacts: () -> Unit) {
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
            Text(tr(lang, "Stay calm. Tap to call — it opens your dialer.", "Manatiling kalmado. I-tap para tumawag."), style = MaterialTheme.typography.bodyMedium, color = SagipColors.Muted)
            Spacer(Modifier.height(Space.xs.dp))
            directory.national.forEach { c -> CallRow(c.label(lang), c.number, c.note, emphasis = c.number == "911") }
            if (profile.hasEmergencyContact) CallRow(profile.emergencyContactName, profile.emergencyContactNumber, tr(lang, "Your emergency contact", "Iyong emergency contact"))
            val place = directory.placeFor(placeText) ?: directory.placeFor(profile.home)
            directory.localContacts(place).take(2).forEach { CallRow(it.label(lang), it.number, place?.title.orEmpty(), sample = it.sample) }
            PillChip(tr(lang, "All contacts", "Lahat ng contact"), selected = false, onClick = onAllContacts)
        }
    }
}
