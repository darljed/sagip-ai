package dev.darl.sagip.data

/**
 * Builds the final LLM prompt from the user profile + retrieved guidance chunks.
 * Implements docs/CHUNK-SCHEMA.md §4. Pure string logic — fully unit-testable
 * without the model.
 *
 * The prompt does three jobs:
 *  1. GROUND the model: answer only from retrieved guidance, don't freelance medicine.
 *  2. PERSONALIZE: surface the profile facts each chunk flags as relevant.
 *  3. ENFORCE TRUST: severity banner for critical, cite sources, append the user's
 *     real emergency contact when a chunk is call_emergency.
 */
object PromptBuilder {

    fun build(query: String, profile: UserProfile, chunks: List<Chunk>): String {
        val lang = profile.preferredLanguage
        val langName = if (lang == Lang.TL) "Tagalog" else "English"
        val hasCritical = chunks.any { it.severity == Severity.CRITICAL }
        val anyCallEmergency = chunks.any { it.callEmergency }

        // Which profile keys are relevant across the retrieved chunks.
        val relevantKeys = chunks.flatMap { it.personalize }.toSet()

        val sb = StringBuilder()

        sb.appendLine("You are SAGIP, an offline emergency guide for the Philippines.")
        sb.appendLine("Answer ONLY from the GUIDANCE below. If the guidance does not cover the")
        sb.appendLine("question, say so plainly — do NOT invent medical advice.")
        sb.appendLine("Respond in $langName. Use short, numbered steps a panicking person can follow.")
        sb.appendLine()

        // --- Personalization block (only surface relevant, non-empty facts) ---
        val profileLines = buildProfileLines(profile, relevantKeys)
        if (profileLines.isNotEmpty()) {
            sb.appendLine("USER PROFILE (surface only what is relevant to the guidance):")
            profileLines.forEach { sb.appendLine("- $it") }
            sb.appendLine()
        }

        // --- Guidance block (grounding + citations) ---
        sb.appendLine("GUIDANCE (cite the Source in your answer):")
        chunks.forEach { c ->
            sb.appendLine("[Source: ${c.title} — ${c.source}] (severity: ${c.severity.key})")
            sb.appendLine(c.text)
            sb.appendLine()
        }

        // --- Rules block ---
        sb.appendLine("Rules:")
        if (hasCritical) {
            sb.appendLine("- This is life-threatening. BEGIN your answer with a bold warning line.")
        }
        if (relevantKeys.contains("allergies") && profile.allergies.isNotEmpty()) {
            sb.appendLine("- The user is allergic to ${profile.allergies.joinToString(", ")}. If any guidance conflicts, warn explicitly.")
        }
        val household = profile.householdLabels()
        if (relevantKeysTouchHousehold(relevantKeys) && household.isNotEmpty()) {
            sb.appendLine("- The household includes: ${household.joinToString(", ")}. Tailor evacuation/first-aid accordingly.")
        }
        if (anyCallEmergency && profile.hasEmergencyContact) {
            sb.appendLine("- End with: \"Call ${profile.emergencyContactName} at ${profile.emergencyContactNumber} as soon as signal returns, or 911.\"")
        } else if (anyCallEmergency) {
            sb.appendLine("- End by telling them to call 911 or local emergency services as soon as signal returns.")
        }

        sb.appendLine()
        sb.appendLine("USER QUESTION: $query")
        return sb.toString().trimEnd()
    }

    private fun buildProfileLines(p: UserProfile, keys: Set<String>): List<String> = buildList {
        if ("blood_type" in keys && p.bloodType.isNotBlank()) add("Blood type: ${p.bloodType}")
        if ("allergies" in keys && p.allergies.isNotEmpty()) add("Allergies: ${p.allergies.joinToString(", ")}")
        if (("conditions" in keys || "medications" in keys)) {
            val parts = buildList {
                if (p.conditions.isNotEmpty()) add("conditions: ${p.conditions.joinToString(", ")}")
                if (p.medications.isNotEmpty()) add("medications: ${p.medications.joinToString(", ")}")
            }
            if (parts.isNotEmpty()) add("Health: ${parts.joinToString("; ")}")
        }
        if (relevantKeysTouchHousehold(keys)) {
            val h = p.householdLabels()
            if (h.isNotEmpty()) add("Household: ${h.joinToString(", ")}")
        }
        if ("home" in keys && p.home.isNotBlank()) add("Home: ${p.home}")
        if ("emergency_contact" in keys && p.hasEmergencyContact) {
            add("Emergency contact: ${p.emergencyContactName} (${p.emergencyContactNumber})")
        }
    }

    private fun relevantKeysTouchHousehold(keys: Set<String>): Boolean =
        keys.any { it.startsWith("household") }
}
