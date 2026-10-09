package dev.darl.sagip.onboarding

import dev.darl.sagip.data.Lang
import dev.darl.sagip.data.UserProfile

/**
 * The chat-style onboarding script. An ordered list of [Question]s; each applies
 * the user's free-text answer to the building [UserProfile]. All questions are
 * skippable (empty / "skip" / "laktawan" leaves the field as-is).
 *
 * Answer parsing is pure + unit-tested (see OnboardingTest) so the chat UI stays
 * thin. Language is asked FIRST so later questions render in the chosen language.
 */
data class Question(
    val id: String,
    val promptEn: String,
    val promptTl: String,
    /** Apply a raw answer to the profile; return the updated profile. */
    val apply: (UserProfile, String) -> UserProfile,
) {
    fun prompt(lang: Lang): String = if (lang == Lang.TL) promptTl else promptEn
}

object Onboarding {

    /** Answers that mean "skip this question". */
    private val SKIP = setOf("skip", "laktawan", "wala", "none", "n/a", "-")

    fun isSkip(answer: String): Boolean = answer.trim().lowercase() in SKIP

    /** yes/no in English + Tagalog. */
    fun parseYesNo(answer: String): Boolean {
        val a = answer.trim().lowercase()
        return a in setOf("yes", "oo", "opo", "y", "meron", "mayroon", "true", "oo may", "yes may")
            || a.startsWith("oo") || a.startsWith("yes") || a.startsWith("meron") || a.startsWith("mayroon")
    }

    /** Extract a dial-able phone number (digits and a leading +). */
    fun parsePhone(answer: String): String {
        val cleaned = answer.filter { it.isDigit() || it == '+' }
        return cleaned.ifBlank { "" }
    }

    /** Comma/'and'/'at' separated list -> trimmed items. */
    fun parseList(answer: String): List<String> {
        if (isSkip(answer) || answer.isBlank()) return emptyList()
        return answer.split(',', ';')
            .flatMap { it.split(Regex("\\s+(and|at)\\s+")) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    val QUESTIONS: List<Question> = listOf(
        Question(
            id = "language",
            promptEn = "First — what language should I use? Type English or Tagalog.",
            promptTl = "Una — anong wika ang gusto mo? I-type ang English o Tagalog.",
            apply = { p, a ->
                val t = a.trim().lowercase()
                val lang = if (t.startsWith("tag") || t.startsWith("fil") || t == "tl") Lang.TL else Lang.EN
                p.copy(preferredLanguage = lang)
            },
        ),
        Question(
            id = "name",
            promptEn = "What's your name?",
            promptTl = "Ano ang pangalan mo?",
            apply = { p, a -> if (isSkip(a)) p else p.copy(name = a.trim()) },
        ),
        Question(
            id = "blood_type",
            promptEn = "What's your blood type? (e.g. O+, A-, or 'skip')",
            promptTl = "Ano ang blood type mo? (hal. O+, A-, o 'laktawan')",
            apply = { p, a -> if (isSkip(a)) p else p.copy(bloodType = a.trim().uppercase()) },
        ),
        Question(
            id = "allergies",
            promptEn = "Any allergies, especially to medicine? (comma-separated, or 'none')",
            promptTl = "May allergy ka ba, lalo sa gamot? (paghiwalayin ng kuwit, o 'wala')",
            apply = { p, a -> p.copy(allergies = parseList(a)) },
        ),
        Question(
            id = "conditions",
            promptEn = "Any medical conditions or medicines you take? (or 'none')",
            promptTl = "May sakit ka ba o gamot na iniinom? (o 'wala')",
            apply = { p, a -> p.copy(conditions = parseList(a)) },
        ),
        Question(
            id = "contact_name",
            promptEn = "Who should I tell you to call in an emergency? (a name)",
            promptTl = "Sino ang dapat kong sabihing tawagan sa emergency? (pangalan)",
            apply = { p, a -> if (isSkip(a)) p else p.copy(emergencyContactName = a.trim()) },
        ),
        Question(
            id = "contact_number",
            promptEn = "Their phone number?",
            promptTl = "Ano ang numero nila?",
            apply = { p, a -> p.copy(emergencyContactNumber = parsePhone(a)) },
        ),
        Question(
            id = "home",
            promptEn = "What's your barangay / city? (helps with evacuation advice)",
            promptTl = "Anong barangay / lungsod mo? (nakakatulong sa payo sa paglikas)",
            apply = { p, a -> if (isSkip(a)) p else p.copy(home = a.trim()) },
        ),
        Question(
            id = "household_infant",
            promptEn = "Is there an infant or small child at home? (yes/no)",
            promptTl = "May sanggol o maliit na bata ba sa bahay? (oo/hindi)",
            apply = { p, a -> p.copy(householdInfant = parseYesNo(a)) },
        ),
        Question(
            id = "household_elderly",
            promptEn = "Any elderly or person with a disability at home? (yes/no)",
            promptTl = "May matanda o PWD ba sa bahay? (oo/hindi)",
            apply = { p, a ->
                val yes = parseYesNo(a)
                p.copy(householdElderly = yes, householdPwd = yes)
            },
        ),
    )

    /** Build the final profile by folding all answers over an empty profile. */
    fun applyAnswer(profile: UserProfile, questionIndex: Int, answer: String): UserProfile {
        val q = QUESTIONS.getOrNull(questionIndex) ?: return profile
        return q.apply(profile, answer)
    }
}
