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
            val geo = android.location.Geocoder(this, java.util.Locale.getDefault())
            @Suppress("DEPRECATION")
            val addr = geo.getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            val place = listOfNotNull(
                addr?.subLocality,
                addr?.locality ?: addr?.subAdminArea,
            ).distinct().filter { it.isNotBlank() }.joinToString(", ")
            onLocation?.invoke(place)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent { SagipApp(this) }
    }
}

@Composable
private fun SagipApp(activity: MainActivity) {
    val context = LocalContext.current.applicationContext
    val store = remember { ProfileStore(context) }

    // Three phases on first run: wizard -> success -> chat. Returning users skip to chat.
    var profile by remember {
        mutableStateOf(if (store.isOnboarded) (store.load() ?: UserProfile()) else null)
    }
    var savedProfile by remember { mutableStateOf<UserProfile?>(null) } // set when success shown

    when {
        profile != null -> ChatApp(profile!!, activity)

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
                onUseLocation = { activity.useMyLocation { place -> if (place.isNotBlank()) vm.setValue(place) } },
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

@Composable
private fun ChatApp(profile: UserProfile, activity: MainActivity) {
    val context = LocalContext.current.applicationContext
    val vm = remember(profile) {
        val repo = PackRepository.fromAssets(context)
        val retriever = KeywordRetriever(repo)
        ChatViewModel(context, retriever, profile).also { it.initEngine() }
    }
    val state by vm.state.collectAsState()

    // DEBUG-ONLY headless test hook (compiled in, active only on debuggable builds):
    //   adb shell am broadcast -a dev.darl.sagip.DEBUG_ASK --es q "<query>" -p dev.darl.sagip
    // Drives the REAL ChatViewModel (retrieve -> prompt -> engine -> watchdog) with no UI
    // taps and logs the final answer under tag SagipTest.
    val debuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (debuggable) {
        androidx.compose.runtime.DisposableEffect(vm) {
            val r = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                    val q = i?.getStringExtra("q") ?: return
                    android.util.Log.i("SagipTest", "ASK: $q")
                    vm.send(q)
                }
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context, r, android.content.IntentFilter("dev.darl.sagip.DEBUG_ASK"),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
            )
            onDispose { runCatching { context.unregisterReceiver(r) } }
        }
        androidx.compose.runtime.LaunchedEffect(state.busy, state.messages.size) {
            val last = state.messages.lastOrNull()
            if (!state.busy && last != null && last.role == dev.darl.sagip.chat.Role.ASSISTANT && !last.streaming) {
                android.util.Log.i("SagipTest", "DONE model=${state.modelName} status=${state.modelStatus} sev=${last.severity} src=${last.sources}\n>>>\n${last.text}\n<<<")
            }
        }
    }

    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var voiceHint by remember { mutableStateOf<String?>(null) }
    var voiceNeedsPack by remember { mutableStateOf(false) }
    val langTag = if (profile.preferredLanguage == dev.darl.sagip.data.Lang.TL) "fil-PH" else "en-PH"

    ChatScreen(
        state = state,
        input = input,
        onInputChange = { input = it; voiceHint = null; voiceNeedsPack = false },
        onSend = { if (input.isNotBlank()) { vm.send(input); input = ""; voiceHint = null } },
        listening = listening,
        voiceHint = voiceHint,
        onVoiceHintClick = if (voiceNeedsPack) ({ activity.openVoiceDownloadSettings() }) else null,
        onMic = {
            if (listening) {
                activity.stopVoiceInput(); listening = false
            } else {
                voiceHint = null; voiceNeedsPack = false
                activity.startVoiceInput(
                    languageTag = langTag,
                    onPartial = { input = it },
                    onFinal = { input = it; listening = false },
                    onState = { listening = it },
                    onError = {
                        listening = false
                        if (it == "NEEDS_PACK") {
                            voiceNeedsPack = true
                            voiceHint = "Voice needs a language pack — tap to download"
                        } else {
                            voiceHint = it
                        }
                    },
                )
            }
        },
    )
}
