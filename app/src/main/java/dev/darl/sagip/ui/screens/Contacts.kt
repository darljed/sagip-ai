package dev.darl.sagip.ui.screens

import dev.darl.sagip.ui.components.IconPill
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
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

/** Where the shown contacts come from — surfaced to the user so GPS-based results are never a surprise. */
private enum class Source { GPS, SAVED, SEARCH, NONE }

@Composable
fun ContactsScreen(
    directory: ContactDirectory,
    profile: UserProfile,
    gpsText: String,
    onSaveEmergencyContact: (name: String, number: String) -> Unit,
    onPickPhoneContact: ((String, String) -> Unit) -> Unit,
    onLocate: () -> Unit,
) {
    val lang = LocalLang.current
    val gpsPlace = directory.placeFor(gpsText)
    val homePlace = directory.placeFor(profile.home)
    var chosen by remember { mutableStateOf<Place?>(null) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val place: Place? = chosen ?: gpsPlace ?: homePlace
    val source = when {
        chosen != null -> Source.SEARCH
        gpsPlace != null -> Source.GPS
        homePlace != null -> Source.SAVED
        else -> Source.NONE
    }
    val local = directory.localContacts(place)
    val results = remember(query, directory) { directory.search(query) }

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
        item {
            IconPill(
                if (profile.hasEmergencyContact) Icons.Outlined.Edit else Icons.Outlined.PersonAdd,
                if (profile.hasEmergencyContact) tr(lang, "Edit emergency contact", "I-edit ang emergency contact") else tr(lang, "Add emergency contact", "Magdagdag ng emergency contact"),
                { editing = true },
            )
        }

        item { Section(tr(lang, "Near you", "Malapit sa iyo")) }
        item {
            // Source indicator: GPS / saved address / searched place.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SagipColors.SurfaceStrong).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (source == Source.GPS) Icons.Outlined.MyLocation else Icons.Outlined.LocationOn, null,
                    tint = if (source == Source.GPS) SagipColors.Ok else SagipColors.Muted, modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(place?.title ?: tr(lang, "Area not found", "Hindi makita ang lugar"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (source) {
                            Source.GPS -> tr(lang, "Based on your GPS location", "Batay sa GPS location mo")
                            Source.SAVED -> tr(lang, "Based on your saved address (GPS not used)", "Batay sa naka-save mong address (hindi GPS)")
                            Source.SEARCH -> tr(lang, "Searched location", "Hinanap na lokasyon")
                            Source.NONE -> tr(lang, "No local numbers for your area yet", "Wala pang lokal na numero sa lugar mo")
                        },
                        style = MaterialTheme.typography.labelMedium, color = if (source == Source.GPS) SagipColors.Ok else SagipColors.Muted,
                    )
                }
            }
        }
        if (chosen != null || source != Source.GPS) {
            item {
                IconPill(
                    Icons.Outlined.MyLocation,
                    if (chosen != null) tr(lang, "Back to my location", "Bumalik sa lokasyon ko") else tr(lang, "Use my location (GPS)", "Gamitin ang lokasyon ko (GPS)"),
                    { if (chosen != null) chosen = null else onLocate() },
                )
            }
        }
        item { Section(tr(lang, "Find another location", "Maghanap ng ibang lokasyon")) }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = SagipColors.Muted) },
                placeholder = { Text(tr(lang, "City or barangay, e.g. Makati", "Lungsod o barangay, hal. Makati"), color = SagipColors.Muted) },
                textStyle = MaterialTheme.typography.bodyMedium, shape = CircleShape,
            )
        }
        if (query.length >= 2 && results.isEmpty()) {
            item { Text(tr(lang, "No contacts for that place yet.", "Wala pang contact para sa lugar na iyon."), style = MaterialTheme.typography.bodyMedium, color = SagipColors.Muted) }
        }
        items(results) { r ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).background(SagipColors.Card)
                    .border(1.dp, SagipColors.Line, RoundedCornerShape(18.dp)).clickable { chosen = r; query = "" }
                    .padding(horizontal = Space.lg.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.LocationOn, null, tint = SagipColors.Muted)
                Spacer(Modifier.width(12.dp))
                Text(r.title, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (local.isEmpty()) {
            item {
                Text(
                    tr(lang, "Search another city or barangay below, or call 911.", "Maghanap ng ibang lungsod o barangay sa ibaba, o tumawag sa 911."),
                    style = MaterialTheme.typography.bodyMedium, color = SagipColors.Muted,
                )
            }
        } else {
            items(local) { c -> CallRow(c.label(lang), c.number, c.note, sample = c.sample) }
        }

        if (query.isBlank() && directory.areas.isNotEmpty()) {
            item {
                Text(
                    tr(lang, "Areas with contacts: ", "Mga lugar na may contact: ") + directory.areas.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall, color = SagipColors.Muted,
                )
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
                IconPill(Icons.Outlined.Contacts, tr(lang, "Pick from phone contacts", "Pumili sa contacts ng phone"), { onPick { n, num -> name = n; number = num } })
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
