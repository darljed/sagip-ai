package dev.darl.sagip.onboarding

import androidx.lifecycle.ViewModel
import dev.darl.sagip.data.Lang
import dev.darl.sagip.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OnbRole { SAGIP, USER }

data class OnbLine(val role: OnbRole, val text: String)

data class OnboardingState(
    val transcript: List<OnbLine> = emptyList(),
    val index: Int = 0,
    val total: Int = Onboarding.QUESTIONS.size,
    val lang: Lang = Lang.EN,
    val done: Boolean = false,
    val profile: UserProfile = UserProfile(),
)

/**
 * Drives chat-style onboarding: shows one question at a time, applies each typed
 * answer to the building profile, advances, and finishes. Pure state machine —
 * persistence + navigation happen in the host composable.
 */
class OnboardingViewModel : ViewModel() {

    private val _state = MutableStateFlow(
        OnboardingState(
            transcript = listOf(
                OnbLine(OnbRole.SAGIP, "Kumusta! I'm SAGIP, your offline emergency guide. " +
                    "Let me set up a few things so my advice fits you. You can type 'skip' anytime."),
                OnbLine(OnbRole.SAGIP, Onboarding.QUESTIONS.first().prompt(Lang.EN)),
            )
        )
    )
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    /** Submit the user's answer to the current question. */
    fun answer(raw: String) {
        val s = _state.value
        if (s.done) return
        val answer = raw.trim()

        // Echo the user's answer (or a 'skipped' note) into the transcript.
        val echoed = if (answer.isBlank()) "(skip)" else answer
        var profile = Onboarding.applyAnswer(s.profile, s.index, answer.ifBlank { "skip" })

        // Language is Q0 — adopt it immediately so the rest renders localized.
        val lang = profile.preferredLanguage

        val nextIndex = s.index + 1
        val transcript = s.transcript + OnbLine(OnbRole.USER, echoed)

        if (nextIndex >= Onboarding.QUESTIONS.size) {
            val closing = if (lang == Lang.TL)
                "Salamat! Handa na ako. Itanong mo kung ano ang emergency."
            else
                "Thanks! I'm ready. Ask me about any emergency."
            _state.value = s.copy(
                transcript = transcript + OnbLine(OnbRole.SAGIP, closing),
                index = nextIndex, lang = lang, done = true, profile = profile,
            )
        } else {
            val nextPrompt = Onboarding.QUESTIONS[nextIndex].prompt(lang)
            _state.value = s.copy(
                transcript = transcript + OnbLine(OnbRole.SAGIP, nextPrompt),
                index = nextIndex, lang = lang, profile = profile,
            )
        }
    }

    /** Skip the rest of onboarding — finish with whatever's collected so far. */
    fun skipAll() {
        val s = _state.value
        _state.value = s.copy(
            transcript = s.transcript + OnbLine(OnbRole.SAGIP, "No problem — you can set this up later."),
            done = true,
        )
    }
}
