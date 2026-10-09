package dev.darl.sagip

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.darl.sagip.chat.ChatScreen
import dev.darl.sagip.chat.ChatViewModel
import dev.darl.sagip.data.KeywordRetriever
import dev.darl.sagip.data.PackRepository
import dev.darl.sagip.data.UserProfile

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the screen on: model load/inference is memory-sensitive and screen-off
        // perturbs Android memory/power management. Also correct for an emergency app
        // — it shouldn't sleep mid-crisis.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent { SagipApp() }
    }
}

@Composable
private fun SagipApp() {
    val context = LocalContext.current.applicationContext

    // Build only the lightweight, main-thread-safe pieces here. The LLM engine
    // (multi-GB model load) is created ASYNC inside the ViewModel — never on the
    // composition/main thread, which would ANR/crash.
    val vm = remember {
        val repo = PackRepository.fromAssets(context)
        val retriever = KeywordRetriever(repo)
        val profile = UserProfile.DEMO
        ChatViewModel(context, retriever, profile).also { it.initEngine() }
    }

    val state by vm.state.collectAsState()
    ChatScreen(state = state, onSend = vm::send)
}
