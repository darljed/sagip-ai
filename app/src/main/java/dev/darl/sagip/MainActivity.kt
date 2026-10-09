package dev.darl.sagip

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import dev.darl.sagip.onboarding.OnboardingScreen
import dev.darl.sagip.onboarding.OnboardingViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent { SagipApp() }
    }
}

@Composable
private fun SagipApp() {
    val context = LocalContext.current.applicationContext
    val store = remember { ProfileStore(context) }

    // First launch (not onboarded) -> onboarding. Otherwise straight to chat.
    var profile by remember { mutableStateOf(if (store.isOnboarded) (store.load() ?: UserProfile()) else null) }

    if (profile == null) {
        val onbVm: OnboardingViewModel = viewModel()
        val onbState by onbVm.state.collectAsState()
        OnboardingScreen(
            state = onbState,
            onAnswer = { onbVm.answer(it) },
            onSkipAll = { onbVm.skipAll() },
        )
        // When onboarding finishes, persist and switch to chat.
        androidx.compose.runtime.LaunchedEffect(onbState.done) {
            if (onbState.done) {
                store.save(onbState.profile)
                profile = onbState.profile
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
