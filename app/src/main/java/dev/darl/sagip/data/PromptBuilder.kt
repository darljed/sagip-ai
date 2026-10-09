package dev.darl.sagip.data

/**
 * Builds the LLM prompt from the user profile + retrieved guidance chunks.
 *
 * Tuned for SMALL models (Gemma 3 1B). Lessons from on-device testing:
 *  - A 1B model PARROTS abstract meta-rules if you lead with them. So we lead with
 *    the TASK and the actual GUIDANCE STEPS, and keep rules to a terse minimum.
 *  - It obeys the LAST instruction best (recency), so the "answer in <language>"
 *    instruction goes at the very end, right before generation.
 *  - It needs an explicit output shape ("rewrite these steps as a numbered list")
 *    or it drifts into commentary.
 *
 * Pure string logic — fully unit-testable without the model.
 */
object PromptBuilder {

    fun build(query: String, profile: UserProfile, chunks: List<Chunk>): String {
        val lang = profile.preferredLanguage
        val langName = if (lang == Lang.TL) "Tagalog (Filipino)" else "English"
        val hasCritical = chunks.any { it.severity == Severity.CRITICAL }
        val relevantKeys = chunks.flatMap { it.personalize }.toSet()
        val sb = StringBuilder()

        // 1. Role + task, stated once, concretely.
        sb.appendLine("You are SAGIP, an offline first-aid and disaster guide for the Philippines.")
        sb.appendLine("A person facing an emergency asks: \"$query\"")
        sb.appendLine()

        // 2. The guidance FIRST and prominent — this is what we want it to output.
        sb.appendLine("Use ONLY these official steps to answer:")
        chunks.forEach { c ->
            sb.appendLine()
            sb.appendLine("### ${c.title} (source: ${c.source})")
            sb.appendLine(c.text)
        }
        sb.appendLine()

        // 3. Personalization — concrete facts to weave in, not abstract rules.
        val personalLines = personalNotes(profile, relevantKeys, chunks)
        if (personalLines.isNotEmpty()) {
            sb.appendLine("Personalise for this person:")
            personalLines.forEach { sb.appendLine("- $it") }
            sb.appendLine()
        }

        // 4. Output shape — tell it exactly what to produce. Minimal, concrete.
        sb.appendLine("Write the answer as short numbered steps the person can follow right now.")
        if (hasCritical) {
            val leadIn = if (lang == Lang.TL)
                "Start with one short line warning that this is a life-threatening emergency (write that line in Tagalog too)."
            else
                "Start with one short line: this is an emergency, get help fast."
            sb.appendLine(leadIn)
        }
        sb.appendLine("Use only the steps above. If something is not covered, say you don't have that info.")

        // 5. Language instruction LAST (recency) — strongest placement for a 1B model.
        sb.appendLine()
        if (lang == Lang.TL) {
            sb.appendLine("IMPORTANT: Write your entire answer in $langName. Do not use English.")
        } else {
            sb.appendLine("Write your entire answer in $langName.")
        }
        sb.append("Answer:")
        return sb.toString()
    }

    /** Concrete, weave-in personalization notes (not abstract rules the model echoes). */
    private fun personalNotes(p: UserProfile, keys: Set<String>, chunks: List<Chunk>): List<String> = buildList {
        if ("allergies" in keys && p.allergies.isNotEmpty())
            add("Allergic to ${p.allergies.joinToString(", ")} — warn if any step risks this.")
        if ("blood_type" in keys && p.bloodType.isNotBlank())
            add("Blood type ${p.bloodType}.")
        if (keys.any { it.startsWith("household") }) {
            val h = p.householdLabels()
            if (h.isNotEmpty()) add("Household has ${h.joinToString(", ")} — prioritise their safety.")
        }
        if (("conditions" in keys || "medications" in keys)) {
            val meds = (p.conditions + p.medications)
            if (meds.isNotEmpty()) add("Has ${meds.joinToString(", ")}.")
        }
        // Emergency-contact callback is appended by the UI (deterministic), not left
        // to the model — so it's always correct. We still hint it here if relevant.
        if (chunks.any { it.callEmergency } && p.hasEmergencyContact)
            add("End by telling them to call ${p.emergencyContactName} (${p.emergencyContactNumber}) or 911 when signal returns.")
    }
}
