package dev.darl.sagip

import android.os.Bundle
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
import dev.darl.sagip.llm.LlmEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SagipApp() }
    }
}

@Composable
private fun SagipApp() {
    val context = LocalContext.current

    // Build the model-independent pipeline now. Profile = DEMO until onboarding ships.
    val vm = remember {
        val repo = PackRepository.fromAssets(context)
        val retriever = KeywordRetriever(repo)
        val profile = UserProfile.DEMO
        val modelReady = LlmEngine.modelExists()

        // Real streaming generator if the model is on device; otherwise the mock
        // that composes from retrieved chunks so the full UI works pre-model.
        val generate: suspend (String, (String, Boolean) -> Unit) -> Unit =
            if (modelReady) {
                val engine = LlmEngine.create(context)
                val fn: suspend (String, (String, Boolean) -> Unit) -> Unit =
                    { prompt, onPartial -> engine.generateAsync(prompt, onPartial) }
                fn
            } else {
                ChatViewModel.mockGenerator(retriever, profile)
            }

        ChatViewModel(retriever, profile, generate, modelReady)
    }

    val state by vm.state.collectAsState()
    ChatScreen(state = state, onSend = vm::send)
}
