package dev.darl.sagip.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.darl.sagip.data.Chunk
import dev.darl.sagip.data.ContactDirectory
import dev.darl.sagip.data.Lang
import dev.darl.sagip.data.PromptBuilder
import dev.darl.sagip.data.Retriever
import dev.darl.sagip.data.Severity
import dev.darl.sagip.data.TopicRepository
import dev.darl.sagip.data.UserProfile
import dev.darl.sagip.llm.LlmEngine
import dev.darl.sagip.llm.ModelConfig
import dev.darl.sagip.llm.cleanAnswer
import dev.darl.sagip.llm.looksRepetitive
import dev.darl.sagip.llm.trimRepetitionTail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class Role { USER, ASSISTANT }

enum class ModelStatus { LOADING, READY, MOCK, ERROR }

/** A guide the answer was grounded in; rendered as a tappable card under the reply. */
data class RelatedGuide(val topicId: String, val title: String, val severity: Severity)

/** A chat-level contact chip (personal contact, barangay…). 911 lives in the top bar, not here. */
data class ContactChip(val label: String, val number: String)

data class Message(
    val role: Role,
    val text: String,
    val severity: Severity? = null,
    val related: List<RelatedGuide> = emptyList(),
    val contacts: List<ContactChip> = emptyList(),
    val streaming: Boolean = false,
)

data class ChatState(
    val messages: List<Message> = emptyList(),
    val busy: Boolean = false,
    val modelStatus: ModelStatus = ModelStatus.LOADING,
    val modelName: String = "",
    val backend: String = "",
)

/**
 * SAGIP as a support agent: understand the concern -> retrieve guides from the packs ->
 * let Gemma 4 explain them for this person -> attach related guides + relevant contacts.
 * The model never answers from memory; pack text is the only source it is shown.
 */
class ChatViewModel(
    private val appContext: Context,
    private val retriever: Retriever,
    private val topics: TopicRepository,
    private val directory: ContactDirectory,
    private val profile: UserProfile,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    @Volatile private var engine: LlmEngine? = null

    /** Load the model off the main thread; the UI works (pack fallback) while it loads. */
    fun initEngine() {
        val resolved = ModelConfig.resolve()
        if (resolved == null) {
            _state.value = _state.value.copy(modelStatus = ModelStatus.MOCK, modelName = "no model")
            return
        }
        _state.value = _state.value.copy(modelStatus = ModelStatus.LOADING, modelName = resolved.displayName)
        viewModelScope.launch {
            try {
                val eng = withContext(Dispatchers.IO) { LlmEngine.create(appContext) }
                engine = eng
                _state.value = _state.value.copy(modelStatus = ModelStatus.READY, modelName = resolved.displayName, backend = eng.backendLabel)
            } catch (t: Throwable) {
                android.util.Log.e("SagipLlm", "engine load failed", t)
                _state.value = _state.value.copy(modelStatus = ModelStatus.ERROR, modelName = resolved.displayName)
            }
        }
    }

    fun send(userText: String) {
        val text = userText.trim()
        if (text.isEmpty() || _state.value.busy) return
        val lang = profile.preferredLanguage

        val previous = _state.value.messages
        val lastUser = previous.lastOrNull { it.role == Role.USER }?.text
        // Short follow-ups ("what about kids?") borrow the previous question — but ONLY when
        // the message finds nothing on its own, so a new topic is never hijacked by the last one.
        var chunks = retriever.retrieve(text, lang, k = 3)
        if (chunks.isEmpty() && lastUser != null && text.split(Regex("\\s+")).size <= 4) {
            chunks = retriever.retrieve("$lastUser $text", lang, k = 3)
        }
        val history = previous.chunked(2).takeLast(2).mapNotNull { pair ->
            val u = pair.firstOrNull { it.role == Role.USER }?.text
            val a = pair.firstOrNull { it.role == Role.ASSISTANT }?.text
            if (u != null && a != null) u to a else null
        }

        val withUser = _state.value.messages + Message(Role.USER, text)
        _state.value = _state.value.copy(messages = withUser, busy = true)

        val idx = withUser.size

        if (chunks.isEmpty()) {
            val msg = if (lang == Lang.TL)
                "Wala akong nakitang gabay na tumutugma dito sa mga offline na pack. Subukan mong hanapin ito sa Maghanap o i-rephrase ang tanong. Kung may panganib ngayon, i-tap ang SOS sa itaas."
            else
                "I couldn't find a guide that matches this in the offline packs. Try Search or rephrase your question. If anyone is in danger right now, tap SOS at the top."
            _state.value = _state.value.copy(
                messages = withUser + Message(Role.ASSISTANT, msg, contacts = personalContacts()),
                busy = false,
            )
            return
        }

        val related = chunks.mapNotNull { topics.forChunk(it) }.distinctBy { it.id }
            .map { RelatedGuide(it.id, it.title(lang), it.severity) }
        // Retrieval returns best-first, so the lead guide decides the banner (not the loudest
        // secondary one — a snake *sighting* must not be labelled life-threatening).
        val severity = chunks.first().severity
        val contacts = if (chunks.any { it.callEmergency }) personalContacts() else emptyList()
        _state.value = _state.value.copy(
            messages = withUser + Message(Role.ASSISTANT, "", severity, related, contacts, streaming = true),
        )

        val prompt = PromptBuilder.build(text, profile, chunks, history)
        val fallback = packFallback(chunks)

        viewModelScope.launch {
            val eng = engine
            var finalText = fallback
            try {
                if (eng != null) {
                    for (attempt in 0..1) {
                        val r = runGeneration(eng, prompt, idx, fallback, attempt)
                        finalText = r.first
                        if (!r.second) break
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("SagipGen", "generation crashed: ${t.message}")
            }
            update(idx, finalText, streaming = false)
            _state.value = _state.value.copy(busy = false)
        }
    }

    /** Returns (text, retryable). Runs the model with a watchdog so the UI can never hang. */
    private suspend fun runGeneration(eng: LlmEngine, prompt: String, idx: Int, fallback: String, attempt: Int): Pair<String, Boolean> {
        val sb = StringBuilder()
        val finished = CompletableDeferred<String>()
        val retryable = AtomicBoolean(false)
        val stopped = AtomicBoolean(false)
        val lastProgress = AtomicLong(System.currentTimeMillis())
        val gotFirst = AtomicBoolean(false)
        val t0 = System.currentTimeMillis()

        fun finish(text: String, natural: Boolean = false) {
            if (!stopped.compareAndSet(false, true)) return
            if (!natural) viewModelScope.launch(Dispatchers.IO) { runCatching { eng.cancel() } }
            finished.complete(text)
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                eng.generateAsync(prompt) { partial, done ->
                    if (stopped.get()) return@generateAsync
                    lastProgress.set(System.currentTimeMillis())
                    if (gotFirst.compareAndSet(false, true)) android.util.Log.i("SagipGen", "TTFT ${System.currentTimeMillis() - t0}ms promptChars=${prompt.length}")
                    sb.append(partial)
                    val raw = cleanAnswer(sb.toString())
                    when {
                        !done && looksRepetitive(raw) -> {
                            val t = trimRepetitionTail(raw).trim()
                            finish(if (t.length < 40) fallback else t)
                        }
                        !done && raw.length > 2200 -> finish(trimRepetitionTail(raw).trim().ifBlank { fallback })
                        done -> {
                            android.util.Log.i("SagipGen", "TOTAL ${System.currentTimeMillis() - t0}ms chars=${raw.length}")
                            if (raw.length < 20 && attempt == 0) {
                                android.util.Log.w("SagipGen", "empty answer, retrying once")
                                retryable.set(true)
                                finish("", natural = true)
                            } else finish(if (raw.length < 20) fallback else raw, natural = true)
                        }
                        else -> viewModelScope.launch(Dispatchers.Main) { if (!stopped.get()) update(idx, raw, streaming = true) }
                    }
                }
            }.onFailure {
                android.util.Log.e("SagipGen", "generateAsync failed: ${it.message}")
                finish(fallback)
            }
        }

        val start = System.currentTimeMillis()
        while (!finished.isCompleted) {
            delay(400)
            val now = System.currentTimeMillis()
            val limit = if (gotFirst.get()) STALL_MS else PREFILL_MS
            if (now - lastProgress.get() > limit || now - start > HARD_CAP_MS) {
                android.util.Log.w("SagipGen", "watchdog abort")
                val sofar = trimRepetitionTail(cleanAnswer(sb.toString())).trim()
                finish(if (sofar.length < 40) fallback else sofar)
                break
            }
        }
        return finished.await() to retryable.get()
    }

    /** Chat-level contacts: the person's own emergency contact + their barangay hall if known. */
    private fun personalContacts(): List<ContactChip> = buildList {
        if (profile.hasEmergencyContact) add(ContactChip(profile.emergencyContactName, profile.emergencyContactNumber))
        directory.barangayFor(profile.home)?.contacts?.firstOrNull { it.number != null }?.let {
            add(ContactChip(it.label(profile.preferredLanguage), it.number!!))
        }
    }

    /** Authoritative pack steps (title + numbered list) used when the model fails or stalls. */
    private fun packFallback(chunks: List<Chunk>): String =
        chunks.joinToString("\n\n") { "**${it.title}**\n${it.text.trim()}" }

    private fun update(index: Int, text: String, streaming: Boolean) {
        val msgs = _state.value.messages.toMutableList()
        if (index in msgs.indices) {
            msgs[index] = msgs[index].copy(text = text, streaming = streaming)
            _state.value = _state.value.copy(messages = msgs)
        }
    }

    override fun onCleared() {
        engine?.close()
        engine = null
    }

    companion object {
        private const val STALL_MS = 15_000L
        private const val PREFILL_MS = 30_000L
        private const val HARD_CAP_MS = 60_000L
    }
}
