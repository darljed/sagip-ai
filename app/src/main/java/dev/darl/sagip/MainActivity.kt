package dev.darl.sagip

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.darl.sagip.chat.ChatScreen
import dev.darl.sagip.chat.ChatViewModel
import dev.darl.sagip.data.KeywordRetriever
import dev.darl.sagip.data.PackRepository
import dev.darl.sagip.data.ProfileStore
import dev.darl.sagip.data.UserProfile
import dev.darl.sagip.onboarding.WizardScreen
import dev.darl.sagip.onboarding.OnboardingSuccessScreen
import dev.darl.sagip.onboarding.WizardViewModel
import java.util.Calendar

class MainActivity : ComponentActivity() {

    // Contact picker: returns a content URI; we read the display name AND phone number.
    private var onContactPicked: ((name: String, number: String) -> Unit)? = null
    private val pickContact = registerForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val name = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else ""
                    val number = if (numIdx >= 0) c.getString(numIdx) ?: "" else ""
                    onContactPicked?.invoke(name, number)
                }
            }
        }
    }

    fun pickContact(onResult: (name: String, number: String) -> Unit) {
        onContactPicked = onResult
        pickContact.launch(null)
    }

    // Location: one-tap reverse-geocode to a place name for the barangay/city step.
    private var onLocation: ((String) -> Unit)? = null
    private val requestLocationPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) resolveLocation() else onLocation?.invoke("") }

    fun useMyLocation(onResult: (String) -> Unit) {
        onLocation = onResult
        val perm = android.Manifest.permission.ACCESS_FINE_LOCATION
        if (checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            resolveLocation()
        } else {
            requestLocationPerm.launch(perm)
        }
    }

    // --- Voice input (on-device SpeechRecognizer) ---
    private val voice by lazy { dev.darl.sagip.voice.VoiceInput(this) }
    private var pendingVoiceStart: (() -> Unit)? = null
    private val requestMicPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) pendingVoiceStart?.invoke(); pendingVoiceStart = null }

    fun startVoiceInput(
        languageTag: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onState: (Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        val begin = { voice.start(languageTag, onPartial, onFinal, onState, onError) }
        val perm = android.Manifest.permission.RECORD_AUDIO
        if (checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            begin()
        } else {
            pendingVoiceStart = begin
            requestMicPerm.launch(perm)
        }
    }

    fun stopVoiceInput() = voice.stop()

    /**
     * Opens the system voice-input / offline-languages settings so the user can
     * download the on-device speech pack (e.g. Filipino). Tries the most specific
     * screen first, then falls back to general input settings.
     */
    fun openVoiceDownloadSettings() {
        val candidates = listOf(
            Intent("com.android.settings.action.INPUT_METHOD_SETTINGS"),
            Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(android.provider.Settings.ACTION_SETTINGS),
        )
        for (intent in candidates) {
            if (runCatching { startActivity(intent); true }.getOrDefault(false)) return
        }
    }

    @Suppress("MissingPermission")
    private fun resolveLocation() {
        runCatching {
            val lm = getSystemService(android.location.LocationManager::class.java)
            val loc = lm?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?: lm?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            if (loc == null) { onLocation?.invoke(""); return }
            // Reverse geocoding needs the network on most phones; offline it throws. Always
            // append the raw coordinates so the contact directory can still resolve the area.
            val place = runCatching {
                val geo = android.location.Geocoder(this, java.util.Locale.getDefault())
                @Suppress("DEPRECATION")
                val addr = geo.getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
                listOfNotNull(addr?.subLocality, addr?.locality ?: addr?.subAdminArea)
                    .distinct().filter { it.isNotBlank() }.joinToString(", ")
            }.getOrDefault("")
            val geoToken = "geo:${"%.4f".format(java.util.Locale.US, loc.latitude)},${"%.4f".format(java.util.Locale.US, loc.longitude)}"
            onLocation?.invoke("$place $geoToken".trim())
            return
                    }.onFailure { onLocation?.invoke("") }
    }

    fun pickDate(current: String, onResult: (String) -> Unit) {
        val cal = Calendar.getInstance()
        runCatching {
            val p = current.split("-").map { it.toInt() }
            if (p.size == 3) cal.set(p[0], p[1] - 1, p[2])
        }
        DatePickerDialog(
            this,
            { _, y, m, d -> onResult("%04d-%02d-%02d".format(y, m + 1, d)) },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    /** Status/nav bar icon colours must follow the in-app theme (not just the phone's). */
    fun applySystemBars(dark: Boolean) {
        val style = if (dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent { SagipApp(this) }
    }
}

@Composable
private fun SagipApp(activity: MainActivity) {
    val context = LocalContext.current.applicationContext
    val store = remember { ProfileStore(context) }
    val settings = remember { dev.darl.sagip.data.SettingsStore(context) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }

    // Three phases on first run: wizard -> success -> chat. Returning users skip to chat.
    var profile by remember {
        mutableStateOf(if (store.isOnboarded) (store.load() ?: UserProfile()) else null)
    }
    var savedProfile by remember { mutableStateOf<UserProfile?>(null) } // set when success shown

    dev.darl.sagip.ui.theme.SagipTheme(themeMode) {
        val dark = dev.darl.sagip.ui.theme.isDark(themeMode)
        androidx.compose.runtime.LaunchedEffect(dark) { activity.applySystemBars(dark) }
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(dev.darl.sagip.ui.theme.SagipColors.Paper)) {
        when {
            profile != null -> dev.darl.sagip.ui.MainShell(
                profile!!,
                dev.darl.sagip.ui.VoiceHooks(
                    start = { tag, p, f, st, er -> activity.startVoiceInput(tag, p, f, st, er) },
                    stop = { activity.stopVoiceInput() },
                    openDownloadSettings = { activity.openVoiceDownloadSettings() },
                    pickContact = { cb -> activity.pickContact { n, num -> cb(n, num) } },
                    locate = { cb -> activity.useMyLocation { cb(it) } },
                    pickDate = { cur, cb -> activity.pickDate(cur) { cb(it) } },
                ),
                themeMode = themeMode,
                onThemeChange = { themeMode = it; settings.themeMode = it },
                onProfileChange = { updated -> store.save(updated); profile = updated },
            )

            savedProfile != null -> OnboardingSuccessScreen(
                profile = savedProfile!!,
                onEnter = { profile = savedProfile },
            )

            else -> {
                val vm: WizardViewModel = viewModel()
                val state by vm.state.collectAsState()
                WizardScreen(
                    state = state,
                    onValue = vm::setValue,
                    onToggleChip = vm::toggleChip,
                    onBack = vm::back,
                    onNext = vm::next,
                    onSkip = vm::skip,
                    onPickContact = { activity.pickContact { name, number -> vm.setValue("$name|$number") } },
                    onPickDate = { activity.pickDate(state.value) { vm.setValue(it) } },
                    onUseLocation = { activity.useMyLocation { place -> dev.darl.sagip.data.placeLabel(place).let { if (it.isNotBlank()) vm.setValue(it) } } },
                )
                androidx.compose.runtime.LaunchedEffect(state.done) {
                    if (state.done) {
                        store.save(state.profile)
                        savedProfile = state.profile   // show success page next
                    }
                }
            }
        }
        }
    }
}
