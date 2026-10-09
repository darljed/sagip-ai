package dev.darl.sagip.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.darl.sagip.data.PromptBuilder
import dev.darl.sagip.data.Retriever
import dev.darl.sagip.data.Severity
import dev.darl.sagip.data.UserProfile
import dev.darl.sagip.llm.LlmEngine
import dev.darl.sagip.llm.ModelConfig
import dev.darl.sagip.llm.looksRepetitive
import dev.darl.sagip.llm.trimRepetitionTail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Role { USER, ASSISTANT }

enum class ModelStatus { LOADING, READY, MOCK, ERROR }

data class Message(
    val role: Role,
    val text: String,
    val severity: Severity? = null,
    val sources: List<String> = emptyList(),
    val callContact: String? = null,
    val callNumber: String? = null,
    val showCallActions: Boolean = false,
    val streaming: Boolean = false,
)

data class ChatState(
    val messages: List<Message> = emptyList(),
    val busy: Boolean = false,
    val modelStatus: ModelStatus = ModelStatus.LOADING,
    val modelName: String = "",
)

/**
 * Drives chat: retrieve -> build prompt -> generate.
 *
 * The LLM engine is loaded ASYNCHRONOUSLY ([initEngine]) off the main thread — a
 * multi-GB model load on the UI thread ANRs/crashes the app. Until it's ready the
 * UI is still usable: queries use the mock generator; once the real engine loads,
 * generation switches to it transparently. If no model is present or load fails,
 * we stay on the mock (status MOCK/ERROR) rather than crashing.
 */
class ChatViewModel(
    private val appContext: Context,
    private val retriever: Retriever,
    private val profile: UserProfile,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    @Volatile private var engine: LlmEngine? = null

    /** Kick off async model load. Safe to call once from composition. */
    fun initEngine() {
        val resolved = ModelConfig.resolve()
        if (resolved == null) {
            _state.value = _state.value.copy(modelStatus = ModelStatus.MOCK, modelName = "mock")
            return
        }
        _state.value = _state.value.copy(modelStatus = ModelStatus.LOADING, modelName = resolved.displayName)
        viewModelScope.launch {
            try {
                val eng = withContext(Dispatchers.IO) { LlmEngine.create(appContext) }
                engine = eng
                _state.value = _state.value.copy(modelStatus = ModelStatus.READY, modelName = resolved.displayName)
            } catch (t: Throwable) {
                // Fall back to mock — demo stays alive even if the native load fails.
                _state.value = _state.value.copy(modelStatus = ModelStatus.ERROR, modelName = "mock (${t.message?.take(40)})")
            }
        }
    }

    fun send(userText: String) {
        if (userText.isBlank() || _state.value.busy) return
        val lang = profile.preferredLanguage

        _state.value = _state.value.copy(
            messages = _state.value.messages + Message(Role.USER, userText),
            busy = true,
        )

        val chunks = retriever.retrieve(userText, lang, k = 2)
        val prompt = PromptBuilder.build(userText, profile, chunks)

        val severity = chunks.maxByOrNull { it.severity.ordinal }?.severity
        val sources = chunks.map { it.title + " — " + it.source }.distinct()
        val hasCall = chunks.any { it.callEmergency } && profile.hasEmergencyContact
        val callContact = if (hasCall) "Call ${profile.emergencyContactName}" else null
        val callNumber = if (hasCall) profile.emergencyContactNumber else null
        // SOP: offer tap-to-call (incl. 911) whenever this is a real emergency.
        val showCallActions = chunks.any {
            it.callEmergency || it.severity == Severity.URGENT || it.severity == Severity.CRITICAL
        }

        val assistantIndex = _state.value.messages.size
        _state.value = _state.value.copy(
            messages = _state.value.messages + Message(
                role = Role.ASSISTANT, text = "", severity = severity,
                sources = sources, callContact = callContact, callNumber = callNumber,
                showCallActions = showCallActions, streaming = true,
            )
        )

        viewModelScope.launch {
            val sb = StringBuilder()
            val eng = engine
            try {
                if (eng != null) {
                    var stopped = false
                    withContext(Dispatchers.IO) {
                        eng.generateAsync(prompt) { partial, done ->
                            if (stopped) return@generateAsync
                            sb.append(partial)
                            val raw = sb.toString()
                            if (!done && looksRepetitive(raw)) {
                                // Repetition collapse — show the cleaned prefix and finish.
                                stopped = true
                                val cleaned = trimRepetitionTail(raw)
                                viewModelScope.launch(Dispatchers.Main) {
                                    updateAssistant(assistantIndex, cleaned, streaming = false)
                                    setBusy(false)
                                }
                                return@generateAsync
                            }
                            viewModelScope.launch(Dispatchers.Main) {
                                updateAssistant(assistantIndex, raw, streaming = !done)
                                if (done) setBusy(false)
                            }
                        }
                    }
                } else {
                    // Mock: compose from retrieved guidance so the UI/pipeline works pre-model.
                    val canned = buildMock(prompt)
                    for (word in canned.split(" ")) {
                        sb.append(word).append(' ')
                        updateAssistant(assistantIndex, sb.toString(), streaming = true)
                        kotlinx.coroutines.delay(10)
                    }
                    updateAssistant(assistantIndex, sb.toString(), streaming = false)
                    setBusy(false)
                }
            } catch (t: Throwable) {
                updateAssistant(assistantIndex, "⚠ Generation error: ${t.message}", streaming = false)
                setBusy(false)
            }
        }
    }

    private fun buildMock(prompt: String): String {
        val guidance = prompt.substringAfter("GUIDANCE", "").take(600)
        val steps = guidance.lineSequence()
            .filter { it.trim().matches(Regex("^\\d+\\..*")) }
            .take(5).joinToString("\n")
        return "[mock — model not loaded]\nBased on trusted guidance:\n$steps"
    }

    private fun updateAssistant(index: Int, text: String, streaming: Boolean) {
        val msgs = _state.value.messages.toMutableList()
        if (index in msgs.indices) {
            msgs[index] = msgs[index].copy(text = text, streaming = streaming)
            _state.value = _state.value.copy(messages = msgs)
        }
    }

    private fun setBusy(b: Boolean) { _state.value = _state.value.copy(busy = b) }

    override fun onCleared() {
        engine?.close()
        engine = null
    }
}
