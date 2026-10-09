package dev.darl.sagip.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.darl.sagip.data.Chunk
import dev.darl.sagip.data.Lang
import dev.darl.sagip.data.PromptBuilder
import dev.darl.sagip.data.Retriever
import dev.darl.sagip.data.Severity
import dev.darl.sagip.data.UserProfile
import dev.darl.sagip.llm.LlmEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Role { USER, ASSISTANT }

/**
 * One chat message. Assistant messages carry the trust metadata the UI renders:
 * severity banner, source citations, and whether to show the emergency-call line.
 */
data class Message(
    val role: Role,
    val text: String,
    val severity: Severity? = null,
    val sources: List<String> = emptyList(),
    val callContact: String? = null,  // e.g. "Call Maria at +639171234567"
    val streaming: Boolean = false,
)

data class ChatState(
    val messages: List<Message> = emptyList(),
    val busy: Boolean = false,
    val modelReady: Boolean = false,
)

/**
 * Drives a chat turn: retrieve -> build prompt -> generate.
 *
 * The LLM is injected as a nullable [generate] function. When the model is on the
 * device we pass a real streaming generator; until then (or in previews/tests) we
 * pass a MOCK that composes an answer from the retrieved chunks — so the entire UI
 * and pipeline are exercisable before the .task lands. Swapping to the real model
 * is a single call-site change.
 */
class ChatViewModel(
    private val retriever: Retriever,
    private val profile: UserProfile,
    private val generate: suspend (prompt: String, onPartial: (String, Boolean) -> Unit) -> Unit,
    modelReady: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState(modelReady = modelReady))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    fun send(userText: String) {
        if (userText.isBlank() || _state.value.busy) return
        val lang = profile.preferredLanguage

        // 1. Append the user message.
        _state.value = _state.value.copy(
            messages = _state.value.messages + Message(Role.USER, userText),
            busy = true,
        )

        // 2. Retrieve grounding chunks.
        val chunks = retriever.retrieve(userText, lang, k = 3)
        val prompt = PromptBuilder.build(userText, profile, chunks)

        // 3. Seed a streaming assistant message with trust metadata from the chunks.
        val severity = chunks.maxByOrNull { it.severity.ordinal }?.severity
        val sources = chunks.map { it.title + " — " + it.source }.distinct()
        val callContact = if (chunks.any { it.callEmergency } && profile.hasEmergencyContact)
            "Call ${profile.emergencyContactName} at ${profile.emergencyContactNumber}" else null

        val assistantIndex = _state.value.messages.size
        _state.value = _state.value.copy(
            messages = _state.value.messages + Message(
                role = Role.ASSISTANT, text = "", severity = severity,
                sources = sources, callContact = callContact, streaming = true,
            )
        )

        // 4. Generate (real or mock), streaming partials into the assistant message.
        viewModelScope.launch {
            val sb = StringBuilder()
            withContext(Dispatchers.IO) {
                generate(prompt) { partial, done ->
                    sb.append(partial)
                    updateAssistant(assistantIndex, sb.toString(), streaming = !done)
                    if (done) setBusy(false)
                }
            }
        }
    }

    private fun updateAssistant(index: Int, text: String, streaming: Boolean) {
        val msgs = _state.value.messages.toMutableList()
        if (index in msgs.indices) {
            msgs[index] = msgs[index].copy(text = text, streaming = streaming)
            _state.value = _state.value.copy(messages = msgs)
        }
    }

    private fun setBusy(b: Boolean) {
        _state.value = _state.value.copy(busy = b)
    }

    companion object {
        /**
         * MOCK generator for pre-model development: streams back a readable answer
         * built from the chunks already selected by retrieval. Lets us prove the
         * whole UI + pipeline without the .task file. ponytail: the mock reuses the
         * real retrieval output, so what you see now is structurally what ships.
         */
        fun mockGenerator(
            retriever: Retriever,
            profile: UserProfile,
        ): suspend (String, (String, Boolean) -> Unit) -> Unit = { prompt, onPartial ->
            // The prompt already contains the GUIDANCE block; echo a tidy summary of it.
            val guidance = prompt.substringAfter("GUIDANCE", "").take(500)
            val canned = "[mock answer — model not loaded]\n" +
                "Based on trusted guidance:\n" +
                guidance.lineSequence()
                    .filter { it.trim().matches(Regex("^\\d+\\..*")) }
                    .take(5).joinToString("\n")
            // Stream it word by word to exercise the streaming UI.
            for (word in canned.split(" ")) {
                onPartial("$word ", false)
            }
            onPartial("", true)
        }
    }
}
