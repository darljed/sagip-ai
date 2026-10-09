package dev.darl.sagip.onboarding

import androidx.lifecycle.ViewModel
import dev.darl.sagip.data.Lang
import dev.darl.sagip.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WizardState(
    val index: Int = 0,
    val total: Int = Wizard.count,
    val profile: UserProfile = UserProfile(),
    val value: String = "",          // current step's working value
    val lang: Lang = Lang.EN,
    val done: Boolean = false,
    val showRequiredError: Boolean = false,
) {
    val step: WizardStep get() = Wizard.STEPS[index]
}

/**
 * Drives the onboarding WIZARD: one step per page, Back/Next, required validation.
 * Keeps a working [value] for the current step (so chips/date/text all funnel
 * through one field) and folds it into [profile] on navigation.
 */
class WizardViewModel : ViewModel() {

    private val _state = MutableStateFlow(
        WizardState().let { it.copy(value = it.step.read(it.profile)) }
    )
    val state: StateFlow<WizardState> = _state.asStateFlow()

    fun setValue(v: String) {
        _state.value = _state.value.copy(value = v, showRequiredError = false)
    }

    /** Toggle a chip for multi-select; set for single-select. */
    fun toggleChip(chip: String, multi: Boolean) {
        val cur = _state.value.value
        val next = if (multi) {
            val items = cur.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            if (items.any { it.equals(chip, true) }) items.removeAll { it.equals(chip, true) }
            else items.add(chip)
            items.joinToString(", ")
        } else chip
        setValue(next)
    }

    fun next() {
        val s = _state.value
        if (!Wizard.isValid(s.index, s.value)) {
            _state.value = s.copy(showRequiredError = true); return
        }
        val profile = Wizard.applyStep(s.profile, s.index, s.value)
        val lang = profile.preferredLanguage
        val nextIndex = s.index + 1
        if (nextIndex >= Wizard.count) {
            _state.value = s.copy(profile = profile, lang = lang, done = true)
        } else {
            _state.value = s.copy(
                index = nextIndex, profile = profile, lang = lang,
                value = Wizard.STEPS[nextIndex].read(profile), showRequiredError = false,
            )
        }
    }

    fun back() {
        val s = _state.value
        if (s.index == 0) return
        // Save current value before going back (non-destructive).
        val profile = Wizard.applyStep(s.profile, s.index, s.value)
        val prevIndex = s.index - 1
        _state.value = s.copy(
            index = prevIndex, profile = profile,
            value = Wizard.STEPS[prevIndex].read(profile), showRequiredError = false,
        )
    }

    /** Skip an optional step (clears its value and advances). */
    fun skip() {
        val s = _state.value
        if (s.step.required) { _state.value = s.copy(showRequiredError = true); return }
        setValue("")
        next()
    }
}
