package dev.darl.sagip.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.darl.sagip.data.Chunk
import dev.darl.sagip.data.ContactEntry
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
/** [person] = a named person (the emergency contact), not an office — drives "Tawagan si …" vs "Tawagan ang …". */
data class ContactChip(val label: String, val number: String, val sample: Boolean = false, val person: Boolean = false)

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
    val sessions: List<ChatSession> = emptyList(),
    val currentId: String = "",
    /** One-shot: a number the UI should place a call to right now ("call my wife"). Never persisted. */
    val callNow: String? = null,
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
    private val profileProvider: () -> UserProfile,
    /** Free text describing where the user is right now (GPS address); may be empty. */
    private val placeProvider: () -> String = { "" },
) : ViewModel() {

    private val profile: UserProfile get() = profileProvider()

    private val store = SessionStore(appContext)
    private var sessions: MutableList<ChatSession> = store.load().toMutableList()


    private fun place() = directory.placeFor(placeProvider()) ?: directory.placeFor(profile.home)

    private val _state = MutableStateFlow(ChatState(sessions = emptyList(), currentId = newId()))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    @Volatile private var engine: LlmEngine? = null

    init { _state.value = _state.value.copy(sessions = sessions.toList()) }

    private fun newId() = java.util.UUID.randomUUID().toString()

    /** Persist the current conversation (only if it has at least one finished exchange). */
    private fun persist() {
        val msgs = _state.value.messages.filter { !it.streaming }
        val firstUser = msgs.firstOrNull { it.role == Role.USER }?.text ?: return
        val id = _state.value.currentId
        val s = ChatSession(id, firstUser.take(48), System.currentTimeMillis(), msgs)
        sessions.removeAll { it.id == id }
        sessions.add(0, s)
        _state.value = _state.value.copy(sessions = sessions.toList())
        val snapshot = sessions.toList()
        viewModelScope.launch(Dispatchers.IO) { store.save(snapshot) }
    }

    /** Start a fresh conversation; the previous one stays in history. */
    fun newChat() {
        if (_state.value.busy) return
        _state.value = _state.value.copy(messages = emptyList(), currentId = newId())
    }

    fun openSession(id: String) {
        if (_state.value.busy) return
        val s = sessions.firstOrNull { it.id == id } ?: return
        _state.value = _state.value.copy(messages = s.messages, currentId = s.id)
    }

    fun deleteSession(id: String) {
        sessions.removeAll { it.id == id }
        if (_state.value.currentId == id) _state.value = _state.value.copy(messages = emptyList(), currentId = newId())
        _state.value = _state.value.copy(sessions = sessions.toList())
        val snapshot = sessions.toList()
        viewModelScope.launch(Dispatchers.IO) { store.save(snapshot) }
    }

    fun clearHistory() {
        sessions.clear()
        _state.value = _state.value.copy(messages = emptyList(), currentId = newId(), sessions = emptyList())
        viewModelScope.launch(Dispatchers.IO) { store.save(emptyList()) }
    }

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
        val priorUser = previous.filter { it.role == Role.USER }.map { it.text }
        val shortAndEmpty = chunks.isEmpty() && text.split(Regex("\\s+")).size <= 4
        if (lastUser != null && (shortAndEmpty || FollowUp.looksLike(text))) {
            // "paano kung walang response?" is about the earlier topic (CPR), not about "no signal":
            // retrieve with the conversation's anchor question so the topic is kept.
            FollowUp.contextQuery(priorUser, text)?.let { q ->
                val withContext = retriever.retrieve(q, lang, k = 3)
                if (withContext.isNotEmpty()) chunks = withContext
            }
        }
        // A question about the person's OWN allergies should surface the allergy guide, not whatever else
        // shares a word like "avoid".
        if (ALLERGY_Q.containsMatchIn(text)) {
            val allergy = retriever.retrieve("allergy anaphylaxis allergic reaction", lang, k = 1)
            chunks = (allergy + chunks.filter { c -> allergy.any { it.pack == c.pack } }).distinctBy { it.id }.take(3).ifEmpty { chunks }
        }
        val history = previous.chunked(2).takeLast(2).mapNotNull { pair ->
            val u = pair.firstOrNull { it.role == Role.USER }?.text
            val a = pair.firstOrNull { it.role == Role.ASSISTANT }?.text
            if (u != null && a != null) u to a else null
        }

        val withUser = _state.value.messages + Message(Role.USER, text)
        _state.value = _state.value.copy(messages = withUser, busy = true)

        // Phone-number requests are answered from the contact directory — never by the model.
        QuickIntents.detect(text)?.let { intent ->
            replyWithContacts(withUser, intent, lang, text)
            return
        }

        val idx = withUser.size

        if (chunks.isEmpty()) {
            val msg = if (lang == Lang.TL)
                "Wala akong nakitang gabay para diyan. Ang SAGIP ay para sa mga emergency, first aid, kaligtasan, sakuna at survival — subukan ang Maghanap o gumamit ng ibang salita. Kung may panganib ngayon, i-tap ang SOS sa itaas."
            else
                "I couldn't find a guide for that. SAGIP helps with emergencies, first aid, safety, disasters and survival — try Search or different words. If anyone is in danger now, tap SOS at the top."
            _state.value = _state.value.copy(
                messages = withUser + Message(Role.ASSISTANT, msg),
                busy = false,
            )
            persist()
            return
        }

        val related = chunks.mapNotNull { topics.forChunk(it) }.distinctBy { it.id }
            .map { RelatedGuide(it.id, it.title(lang), it.severity) }
        // Retrieval returns best-first, so the lead guide decides the banner (not the loudest
        // secondary one — a snake *sighting* must not be labelled life-threatening).
        val severity = chunks.first().severity
        // Contacts are offered to the model, which decides whether to suggest them; a chip appears only
        // if the final answer actually mentions that contact.
        val candidates = if (chunks.any { it.callEmergency } || PromptBuilder.isDisaster(chunks)) personalContacts() else emptyList()
        _state.value = _state.value.copy(
            messages = withUser + Message(Role.ASSISTANT, "", severity, related, emptyList(), streaming = true),
        )

        val offices = candidates.filter { it.label != profile.emergencyContactName }.map { it.label to it.number }
        val prompt = PromptBuilder.build(text, profile, chunks, history, followUp = FollowUp.looksLike(text), offices = offices)
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
            if (isOffTopic(finalText)) {
                // Guardrail: the model judged the message out of scope — drop the guides/contacts
                // that retrieval attached by accident and show the fixed scope message.
                setMessage(idx, Message(Role.ASSISTANT, scopeMessage(lang)))
            } else {
                update(idx, finalText, streaming = false)
                mentionedContacts(finalText, candidates).takeIf { it.isNotEmpty() }?.let { chips ->
                    val msgs = _state.value.messages.toMutableList()
                    if (idx in msgs.indices) { msgs[idx] = msgs[idx].copy(contacts = chips); _state.value = _state.value.copy(messages = msgs) }
                }
            }
            _state.value = _state.value.copy(busy = false)
            persist()
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
                            if (isOffTopic(raw)) {
                                finish(raw, natural = true)   // guardrail verdict, not an empty answer
                            } else if (raw.length < 20 && attempt == 0) {
                                android.util.Log.w("SagipGen", "empty answer, retrying once")
                                retryable.set(true)
                                finish("", natural = true)
                            } else finish(if (raw.length < 20) fallback else raw, natural = true)
                        }
                        // Hold the first tokens back while they could still become "OFF_TOPIC".
                        else -> if (!couldBeOffTopic(raw)) viewModelScope.launch(Dispatchers.Main) { if (!stopped.get()) update(idx, raw, streaming = true) }
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

    /** Chat-level contacts: the person's own emergency contact + their local barangay/city contact. */
    private fun personalContacts(): List<ContactChip> = buildList {
        if (profile.hasEmergencyContact) add(ContactChip(profile.emergencyContactName, profile.emergencyContactNumber, person = true))
        directory.localContacts(place()).firstOrNull()?.let { add(it.toChip(profile.preferredLanguage)) }
    }

    fun consumeCall() { _state.value = _state.value.copy(callNow = null) }

    private fun ContactEntry.toChip(lang: Lang) = ContactChip(label(lang), number!!, sample)

    private fun replyWithContacts(withUser: List<Message>, intent: QuickIntent, lang: Lang, asked: String) {
        val tl = lang == Lang.TL
        val (text, chips) = when (intent) {
            is QuickIntent.CallFamily ->
                if (profile.hasEmergencyContact)
                    (if (tl) "Tinatawagan si ${profile.emergencyContactName}… Kung hindi tumuloy, i-tap ang button."
                     else "Calling ${profile.emergencyContactName}… If it doesn't start, tap the button.") to
                        listOf(ContactChip(profile.emergencyContactName, profile.emergencyContactNumber, person = true))
                else
                    (if (tl) "Wala ka pang naka-save na emergency contact. Pumunta sa Contact tab at i-tap ang I-edit para idagdag ito."
                     else "You haven't saved an emergency contact yet. Open the Contacts tab and tap Edit to add one.") to emptyList()
            is QuickIntent.FindContacts -> {
                // An area named in the question wins ("ospital sa makati"); else GPS / saved home.
                val place = directory.placeFor(asked) ?: place()
                val found = directory.find(intent.kinds, place).distinctBy { it.number }.take(6)
                val where = place?.title
                val msg = when {
                    found.isEmpty() -> if (tl) "Wala pa akong numerong naka-save para diyan${where?.let { " sa $it" }.orEmpty()}. Tumawag sa 911 para sa agarang tulong."
                        else "I don't have a saved number for that${where?.let { " in $it" }.orEmpty()} yet. Call 911 for urgent help."
                    where != null -> if (tl) "Narito ang mga numerong maaari mong tawagan malapit sa $where:" else "Here are numbers you can call near $where:"
                    else -> if (tl) "Narito ang mga numerong maaari mong tawagan. (Wala pa akong lokal na numero para sa lugar mo.)"
                        else "Here are numbers you can call. (I don't have local numbers for your area yet.)"
                }
                val list = found.ifEmpty { directory.national.filter { it.kind == "emergency" } }
                msg to list.map { it.toChip(lang) }
            }
        }
        _state.value = _state.value.copy(
            messages = withUser + Message(Role.ASSISTANT, text, contacts = chips), busy = false,
            callNow = if (intent is QuickIntent.CallFamily && profile.hasEmergencyContact) profile.emergencyContactNumber else null,
        )
        persist()
    }

    private fun setMessage(index: Int, m: Message) {
        val msgs = _state.value.messages.toMutableList()
        if (index in msgs.indices) { msgs[index] = m; _state.value = _state.value.copy(messages = msgs) }
    }

    private fun scopeMessage(lang: Lang) = if (lang == Lang.TL)
        "Pasensya na, ang SAGIP ay para lang sa mga emergency, first aid, kaligtasan, sakuna at survival. May maitutulong ba ako sa mga ito? Kung may panganib ngayon, i-tap ang SOS sa itaas."
    else
        "Sorry, SAGIP only helps with emergencies, first aid, safety, disasters and survival. Is there something like that I can help with? If anyone is in danger now, tap SOS at the top."


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
        private val ALLERGY_Q = Regex("allerg|alerhi|alerji", RegexOption.IGNORE_CASE)

        /** Contacts the answer actually mentions (by name or by number) — those get a tap-to-call chip. */
        fun mentionedContacts(answer: String, candidates: List<ContactChip>): List<ContactChip> {
            val digits = answer.filter { it.isDigit() }
            return candidates.filter { c ->
                val num = c.number.filter { it.isDigit() }
                answer.contains(c.label, ignoreCase = true) || (num.length >= 3 && digits.contains(num))
            }
        }

        /** True once the reply is the out-of-scope sentinel the prompt asks the model to emit. */
        fun isOffTopic(text: String) = text.trim().uppercase().replace(" ", "_").startsWith("OFF_TOPIC") ||
            (text.length < 40 && text.uppercase().contains("OFF_TOPIC"))
        /** While streaming: could this short prefix still turn into OFF_TOPIC? Then don't show it. */
        fun couldBeOffTopic(raw: String): Boolean {
            val t = raw.trim().uppercase().replace(" ", "_")
            return t.length < 10 && "OFF_TOPIC".startsWith(t) && t.isNotEmpty()
        }

        private const val STALL_MS = 15_000L
        private const val PREFILL_MS = 30_000L
        private const val HARD_CAP_MS = 60_000L
    }
}
