package dev.darl.sagip.onboarding

import dev.darl.sagip.data.Lang
import dev.darl.sagip.data.UserProfile

/** Input style for a wizard step. */
enum class StepKind { TEXT, CHIPS_SINGLE, CHIPS_MULTI, DATE, LOCATION, CONTACT, REVIEW }

/**
 * One page of the onboarding wizard. [read]/[apply] bridge the UserProfile and the
 * page's working string value. For CHIPS_MULTI, [allowCustom] shows a free-text box
 * so users can add items not in the suggestion chips.
 *
 * The CONTACT step stores "name|number" as its value (set by the device picker);
 * apply splits it. The REVIEW step has no value — it renders the whole profile.
 */
data class WizardStep(
    val id: String,
    val titleEn: String,
    val titleTl: String,
    val kind: StepKind,
    val required: Boolean = false,
    val allowCustom: Boolean = false,
    val chipsEn: List<String> = emptyList(),
    val chipsTl: List<String> = emptyList(),
    val read: (UserProfile) -> String,
    val apply: (UserProfile, String) -> UserProfile,
    val validate: (String) -> Boolean = { true },
) {
    fun title(lang: Lang) = if (lang == Lang.TL) titleTl else titleEn
    fun chips(lang: Lang) = if (lang == Lang.TL && chipsTl.isNotEmpty()) chipsTl else chipsEn
}

object Wizard {

    private fun csv(s: String) = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    private fun strip(items: List<String>) =
        items.filter { it.lowercase() !in setOf("none", "wala") }

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
            read = { it.name }, apply = { p, v -> p.copy(name = v.trim()) },
            validate = { it.isNotBlank() },
        ),
        WizardStep(
            id = "birthday",
            titleEn = "When's your birthday? (helps me explain at the right level)",
            titleTl = "Kailan ang kaarawan mo? (para maiayon ang paliwanag)",
            kind = StepKind.DATE, required = false,
            read = { it.birthday }, apply = { p, v -> p.copy(birthday = v.trim()) },
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
            titleEn = "Any allergies, especially to medicine? Tap any, or type your own.",
            titleTl = "May allergy ka ba, lalo sa gamot? Pumili o i-type ang sarili mo.",
            kind = StepKind.CHIPS_MULTI, required = false, allowCustom = true,
            chipsEn = listOf("Penicillin", "Aspirin", "Ibuprofen", "Peanuts", "Seafood"),
            chipsTl = listOf("Penicillin", "Aspirin", "Ibuprofen", "Mani", "Seafood"),
            read = { it.allergies.joinToString(", ") },
            apply = { p, v -> p.copy(allergies = strip(csv(v))) },
        ),
        WizardStep(
            id = "conditions",
            titleEn = "Any medical conditions or medicines you take? Tap any, or type your own.",
            titleTl = "May sakit o gamot ka ba? Pumili o i-type ang sarili mo.",
            kind = StepKind.CHIPS_MULTI, required = false, allowCustom = true,
            chipsEn = listOf("Asthma", "Diabetes", "Hypertension", "Heart condition"),
            chipsTl = listOf("Hika", "Diabetes", "Alta presyon", "Sakit sa puso"),
            read = { it.conditions.joinToString(", ") },
            apply = { p, v -> p.copy(conditions = strip(csv(v))) },
        ),
        WizardStep(
            id = "contact",
            titleEn = "Who should SAGIP tell you to call in an emergency?",
            titleTl = "Sino ang dapat tawagan sa emergency?",
            kind = StepKind.CONTACT, required = false,
            read = { p -> if (p.hasEmergencyContact) "${p.emergencyContactName}|${p.emergencyContactNumber}" else "" },
            apply = { p, v ->
                val parts = v.split("|")
                val name = parts.getOrNull(0)?.trim().orEmpty()
                val number = parts.getOrNull(1)?.filter { it.isDigit() || it == '+' }.orEmpty()
                p.copy(emergencyContactName = name, emergencyContactNumber = number)
            },
        ),
        WizardStep(
            id = "home",
            titleEn = "Where do you live? (barangay / city — helps with evacuation advice)",
            titleTl = "Saan ka nakatira? (barangay / lungsod — para sa payo sa paglikas)",
            kind = StepKind.LOCATION, required = false,
            read = { it.home }, apply = { p, v -> p.copy(home = v.trim()) },
        ),
        WizardStep(
            id = "household",
            titleEn = "Who lives with you? SAGIP will prioritise their safety. Tap all that apply.",
            titleTl = "Sino ang kasama mo sa bahay? Uunahin ng SAGIP ang kanilang kaligtasan. Pumili ng lahat ng naaangkop.",
            kind = StepKind.CHIPS_MULTI, required = false, allowCustom = true,
            chipsEn = listOf("Spouse / partner", "Baby or toddler", "Young children", "Elderly parent / grandparent", "Someone pregnant", "Person with a disability", "I live alone"),
            chipsTl = listOf("Asawa / partner", "Sanggol o bata pa", "Mga maliliit na anak", "Matandang magulang / lolo't lola", "May buntis", "May kapansanan (PWD)", "Mag-isa lang ako"),
            read = { p ->
                buildList {
                    if (p.householdInfant) add("Baby or toddler")
                    if (p.householdElderly) add("Elderly parent / grandparent")
                    if (p.householdPwd) add("Person with a disability")
                    if (p.householdPregnant) add("Someone pregnant")
                }.joinToString(", ")
            },
            apply = { p, v ->
                val sel = csv(v).map { it.lowercase() }
                fun any(vararg keys: String) = sel.any { s -> keys.any { it in s } }
                p.copy(
                    householdInfant = any("baby", "toddler", "sanggol", "bata"),
                    householdElderly = any("elder", "grandparent", "matanda", "lolo", "lola"),
                    householdPwd = any("disab", "pwd", "kapansanan"),
                    householdPregnant = any("pregn", "buntis"),
                )
            },
        ),
        WizardStep(
            id = "review",
            titleEn = "Does this look right?",
            titleTl = "Tama ba ang mga ito?",
            kind = StepKind.REVIEW, required = false,
            read = { "" }, apply = { p, _ -> p },
        ),
    )

    val count get() = STEPS.size

    fun applyStep(profile: UserProfile, index: Int, value: String): UserProfile =
        STEPS.getOrNull(index)?.apply?.invoke(profile, value) ?: profile

    fun isValid(index: Int, value: String): Boolean {
        val step = STEPS.getOrNull(index) ?: return true
        if (!step.required) return true
        return step.validate(value)
    }
}
