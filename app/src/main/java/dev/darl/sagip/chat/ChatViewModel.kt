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
import dev.darl.sagip.llm.cleanAnswer
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

        // No genuine pack match → do NOT run the model on empty guidance (it would
        // hallucinate). Show a safe, honest fallback that directs to 911. This is the
        // correct behavior for an emergency app: never a confidently-wrong answer.
        if (chunks.isEmpty()) {
            val msg = if (lang == dev.darl.sagip.data.Lang.TL)
                "Wala akong tiyak na gabay para dito na available offline. Kung ito ay emergency, tumawag agad sa 911 o sa pinakamalapit na awtoridad."
            else
                "I don't have specific offline guidance for that. If this is an emergency, call 911 or your nearest authority immediately."
            _state.value = _state.value.copy(
                messages = _state.value.messages + Message(
                    role = Role.ASSISTANT, text = msg, severity = Severity.URGENT,
                    sources = emptyList(), callContact = null, callNumber = null,
                    showCallActions = true, streaming = false,
                ),
                busy = false,
            )
            return
        }

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
            // Pack steps are the EMERGENCY FALLBACK only — used if the model loops,
            // stalls, or produces nothing. In the normal path the model's own grounded
            // answer is shown (it may adapt the guidance to the actual situation).
            val fallback = fallbackFromChunks(chunks, lang)
            try {
                if (eng != null) {
                    var finalText = fallback
                    for (attempt in 0..1) {
                        sb.setLength(0)
                        var stopped = false
                        val retryable = java.util.concurrent.atomic.AtomicBoolean(false)
                        val finished = kotlinx.coroutines.CompletableDeferred<String>()
                        // Watchdog: track the last time the model emitted anything. E2B on
                        // CPU can hang (no done, no partials). If it stalls, we abort to the
                        // pack fallback so the UI NEVER freezes on "typing...".
                        val lastProgress = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
                        val t0 = System.currentTimeMillis()
                        val gotFirstToken = java.util.concurrent.atomic.AtomicBoolean(false)

                        fun finish(text: String, natural: Boolean = false) {
                            if (stopped) return
                            stopped = true
                            // Natural completion: leave the session alone (engine retires it on next call).
                            if (!natural) viewModelScope.launch(Dispatchers.IO) { runCatching { eng.cancel() } }
                            if (!finished.isCompleted) finished.complete(text)
                        }

                        // Launch generation DETACHED: if the native call ever blocks, the
                        // watchdog below must still run and release the UI.
                        viewModelScope.launch(Dispatchers.IO) {
                            runCatching {
                            eng.generateAsync(prompt) { partial, done ->
                                if (stopped) return@generateAsync
                                lastProgress.set(System.currentTimeMillis())
                                if (gotFirstToken.compareAndSet(false, true)) {
                                    android.util.Log.i("SagipGen", "TTFT ${System.currentTimeMillis() - t0}ms promptChars=${prompt.length}")
                                }
                                sb.append(partial)
                                val raw = cleanAnswer(sb.toString())
                                // Loop guard: if the model degenerates, trim to the clean
                                // prefix; if that leaves too little, use the pack fallback.
                                if (!done && looksRepetitive(raw)) {
                                    val trimmed = trimRepetitionTail(raw).trim()
                                    finish(if (trimmed.length < 40) fallback else trimmed)
                                    return@generateAsync
                                }
                                // Safety cap so a runaway generation can't stream forever.
                                if (!done && raw.length > 1600) {
                                    finish(trimRepetitionTail(raw).trim().ifBlank { fallback })
                                    return@generateAsync
                                }
                                if (done) {
                                    android.util.Log.i("SagipGen", "TOTAL ${System.currentTimeMillis() - t0}ms answerChars=${raw.length}")
                                    val ans = raw.trim()
                                    if (ans.length < 20 && attempt == 0) {
                                        // Model stopped immediately / emitted only control tokens.
                                        // Retry once (cheap on GPU) before using the pack text.
                                        android.util.Log.w("SagipGen", "empty answer, retrying once")
                                        retryable.set(true)
                                        finish("", natural = true)
                                    } else finish(if (ans.length < 20) fallback else ans, natural = true)
                                } else {
                                    viewModelScope.launch(Dispatchers.Main) {
                                        if (!stopped) updateAssistant(assistantIndex, raw, streaming = true)
                                    }
                                }
                            }
                            }.onFailure {
                                android.util.Log.e("SagipGen", "generateAsync failed: ${it.message}")
                                finish(fallback)
                            }
                        }

                        // Watchdog loop (runs independently of the generation call): abort
                        // if no progress for STALL_MS, or total time exceeds HARD_CAP_MS.
                        // Falls back to whatever clean text exists, else the pack steps.
                        val start = System.currentTimeMillis()
                        while (!finished.isCompleted) {
                            kotlinx.coroutines.delay(500)
                            val now = System.currentTimeMillis()
                            // Before the first token the CPU is still prefilling the prompt, which
                            // legitimately takes a while on E2B — use a longer window then.
                            val stallLimit = if (gotFirstToken.get()) STALL_MS else PREFILL_MS
                            if (now - lastProgress.get() > stallLimit || now - start > HARD_CAP_MS) {
                                android.util.Log.w("SagipGen", "watchdog abort: stalled/over-cap")
                                val sofar = trimRepetitionTail(cleanAnswer(sb.toString())).trim()
                                finish(if (sofar.length < 40) fallback else sofar)
                                break
                            }
                        }

                        finalText = finished.await()
                        if (!retryable.get()) break
                    }
                    updateAssistant(assistantIndex, finalText, streaming = false)
                    setBusy(false)
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

    /**
     * Pack steps rendered verbatim from the retrieved chunks (already in the user's
     * language via the TL/EN twin). Used as the EMERGENCY FALLBACK when the model
     * loops, stalls, or produces nothing — guarantees the user still gets correct
     * guidance. The normal path shows the model's own grounded answer.
     */
    private fun fallbackFromChunks(chunks: List<dev.darl.sagip.data.Chunk>, lang: dev.darl.sagip.data.Lang): String {
        return chunks.joinToString("\n\n") { it.text.trim() }
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

    companion object {
        /** Abort if the model emits nothing new for this long (hang detection). */
        private const val STALL_MS = 12_000L
        /** Max wait for the FIRST token (CPU prompt prefill on E2B). */
        private const val PREFILL_MS = 45_000L
        /** Absolute cap on one generation; E2B on CPU is slow but must not run forever. */
        private const val HARD_CAP_MS = 90_000L
    }
}
