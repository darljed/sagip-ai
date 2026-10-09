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
import dev.darl.sagip.onboarding.WizardViewModel
import java.util.Calendar

class MainActivity : ComponentActivity() {

    // Contact picker: returns a content URI; we read the primary phone number.
    private var onContactPicked: ((String) -> Unit)? = null
    private val pickContact = registerForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val numberIdx = if (idx >= 0) idx else c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    if (numberIdx >= 0) onContactPicked?.invoke(c.getString(numberIdx) ?: "")
                }
            }
        }
    }

    fun pickContactNumber(onResult: (String) -> Unit) {
        onContactPicked = onResult
        pickContact.launch(null)
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
    var profile by remember {
        mutableStateOf(if (store.isOnboarded) (store.load() ?: UserProfile()) else null)
    }

    if (profile == null) {
        val vm: WizardViewModel = viewModel()
        val state by vm.state.collectAsState()
        WizardScreen(
            state = state,
            onValue = vm::setValue,
            onToggleChip = vm::toggleChip,
            onBack = vm::back,
            onNext = vm::next,
            onSkip = vm::skip,
            onPickContact = { activity.pickContactNumber { vm.setValue(it) } },
            onPickDate = { activity.pickDate(state.value) { vm.setValue(it) } },
        )
        androidx.compose.runtime.LaunchedEffect(state.done) {
            if (state.done) {
                store.save(state.profile)
                profile = state.profile
            }
        }
    } else {
        ChatApp(profile!!)
    }
}

@Composable
private fun ChatApp(profile: UserProfile) {
    val context = LocalContext.current.applicationContext
    val vm = remember(profile) {
        val repo = PackRepository.fromAssets(context)
        val retriever = KeywordRetriever(repo)
        ChatViewModel(context, retriever, profile).also { it.initEngine() }
    }
    val state by vm.state.collectAsState()
    ChatScreen(state = state, onSend = vm::send)
}
