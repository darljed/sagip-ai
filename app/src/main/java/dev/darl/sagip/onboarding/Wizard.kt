package dev.darl.sagip.onboarding

import dev.darl.sagip.data.Lang
import dev.darl.sagip.data.UserProfile

/** Input style for a wizard step. */
enum class StepKind { TEXT, CHIPS_SINGLE, CHIPS_MULTI, DATE, YESNO, PHONE }

/**
 * One page of the onboarding wizard: a single question with a typed input,
 * optional suggestion chips, and required/optional validation.
 *
 * [read]/[apply] bridge the UserProfile and the page's string value so the UI is
 * generic. Pure + unit-tested; the Compose layer just renders by [kind].
 */
data class WizardStep(
    val id: String,
    val titleEn: String,
    val titleTl: String,
    val kind: StepKind,
    val required: Boolean = false,
    /** Suggestion chips (localized via [chips]). */
    val chipsEn: List<String> = emptyList(),
    val chipsTl: List<String> = emptyList(),
    val read: (UserProfile) -> String,
    val apply: (UserProfile, String) -> UserProfile,
    /** Returns an error message (localized) if invalid, else null. */
    val validate: (String) -> Boolean = { true },
) {
    fun title(lang: Lang) = if (lang == Lang.TL) titleTl else titleEn
    fun chips(lang: Lang) = if (lang == Lang.TL && chipsTl.isNotEmpty()) chipsTl else chipsEn
}

object Wizard {

    private fun csv(s: String) = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    val STEPS: List<WizardStep> = listOf(
        WizardStep(
            id = "language",
            titleEn = "Which language should I use?",
            titleTl = "Anong wika ang gagamitin ko?",
            kind = StepKind.CHIPS_SINGLE, required = true,
            chipsEn = listOf("English", "Tagalog"),
            read = { if (it.preferredLanguage == Lang.TL) "Tagalog" else "English" },
            apply = { p, v -> p.copy(preferredLanguage = if (v.startsWith("Tag")) Lang.TL else Lang.EN) },
            validate = { it.isNotBlank() },
        ),
        WizardStep(
            id = "name",
            titleEn = "What's your name?",
            titleTl = "Ano ang pangalan mo?",
            kind = StepKind.TEXT, required = true,
            read = { it.name },
            apply = { p, v -> p.copy(name = v.trim()) },
            validate = { it.isNotBlank() },
        ),
        WizardStep(
            id = "birthday",
            titleEn = "When's your birthday? (helps me explain things at the right level)",
            titleTl = "Kailan ang kaarawan mo? (para maiayon ko ang paliwanag)",
            kind = StepKind.DATE, required = false,
            read = { it.birthday },
            apply = { p, v -> p.copy(birthday = v.trim()) },
        ),
        WizardStep(
            id = "blood_type",
            titleEn = "What's your blood type?",
            titleTl = "Ano ang blood type mo?",
            kind = StepKind.CHIPS_SINGLE, required = false,
            chipsEn = listOf("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-", "Unknown"),
            read = { it.bloodType },
            apply = { p, v -> p.copy(bloodType = if (v == "Unknown") "" else v) },
        ),
        WizardStep(
            id = "allergies",
            titleEn = "Any allergies, especially to medicine?",
            titleTl = "May allergy ka ba, lalo sa gamot?",
            kind = StepKind.CHIPS_MULTI, required = false,
            chipsEn = listOf("Penicillin", "Aspirin", "Ibuprofen", "Peanuts", "Seafood", "None"),
            chipsTl = listOf("Penicillin", "Aspirin", "Ibuprofen", "Mani", "Seafood", "Wala"),
            read = { it.allergies.joinToString(", ") },
            apply = { p, v -> p.copy(allergies = csv(v).filter { it.lowercase() !in setOf("none", "wala") }) },
        ),
        WizardStep(
            id = "conditions",
            titleEn = "Any medical conditions or medicines you take?",
            titleTl = "May sakit ka ba o gamot na iniinom?",
            kind = StepKind.CHIPS_MULTI, required = false,
            chipsEn = listOf("Asthma", "Diabetes", "Hypertension", "Heart condition", "None"),
            chipsTl = listOf("Hika", "Diabetes", "Alta presyon", "Sakit sa puso", "Wala"),
            read = { it.conditions.joinToString(", ") },
            apply = { p, v -> p.copy(conditions = csv(v).filter { it.lowercase() !in setOf("none", "wala") }) },
        ),
        WizardStep(
            id = "contact_name",
            titleEn = "Who should I tell you to call in an emergency?",
            titleTl = "Sino ang dapat tawagan sa emergency?",
            kind = StepKind.TEXT, required = false,
            read = { it.emergencyContactName },
            apply = { p, v -> p.copy(emergencyContactName = v.trim()) },
        ),
        WizardStep(
            id = "contact_number",
            titleEn = "Their phone number? (tap to pick from Contacts)",
            titleTl = "Ano ang numero nila? (pumili mula sa Contacts)",
            kind = StepKind.PHONE, required = false,
            read = { it.emergencyContactNumber },
            apply = { p, v -> p.copy(emergencyContactNumber = v.filter { c -> c.isDigit() || c == '+' }) },
        ),
        WizardStep(
            id = "home",
            titleEn = "What's your barangay / city?",
            titleTl = "Anong barangay / lungsod mo?",
            kind = StepKind.TEXT, required = false,
            read = { it.home },
            apply = { p, v -> p.copy(home = v.trim()) },
        ),
        WizardStep(
            id = "household",
            titleEn = "Who's at home with you?",
            titleTl = "Sino ang kasama mo sa bahay?",
            kind = StepKind.CHIPS_MULTI, required = false,
            chipsEn = listOf("Infant / small child", "Elderly", "Person with disability", "Pregnant"),
            chipsTl = listOf("Sanggol / maliit na bata", "Matanda", "PWD", "Buntis"),
            read = { p ->
                buildList {
                    if (p.householdInfant) add("Infant / small child")
                    if (p.householdElderly) add("Elderly")
                    if (p.householdPwd) add("Person with disability")
                    if (p.householdPregnant) add("Pregnant")
                }.joinToString(", ")
            },
            apply = { p, v ->
                val sel = csv(v).map { it.lowercase() }
                p.copy(
                    householdInfant = sel.any { "infant" in it || "sanggol" in it || "bata" in it },
                    householdElderly = sel.any { "elder" in it || "matanda" in it },
                    householdPwd = sel.any { "disab" in it || "pwd" in it },
                    householdPregnant = sel.any { "pregn" in it || "buntis" in it },
                )
            },
        ),
    )

    val count get() = STEPS.size

    fun applyStep(profile: UserProfile, index: Int, value: String): UserProfile =
        STEPS.getOrNull(index)?.apply?.invoke(profile, value) ?: profile

    /** Required steps must have a non-blank value to advance. */
    fun isValid(index: Int, value: String): Boolean {
        val step = STEPS.getOrNull(index) ?: return true
        if (!step.required) return true
        return step.validate(value)
    }
}
