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

    fun build(
        query: String,
        profile: UserProfile,
        chunks: List<Chunk>,
        history: List<Pair<String, String>> = emptyList(),
    ): String {
        val lang = profile.preferredLanguage
        val langName = if (lang == Lang.TL) "Tagalog (Filipino)" else "English"
        val hasCritical = chunks.any { it.severity == Severity.CRITICAL }
        val relevantKeys = chunks.flatMap { it.personalize }.toSet()
        val sb = StringBuilder()

        // 1. Role + task, stated once, concretely.
        sb.appendLine("You are SAGIP, an offline first-aid and disaster guide for the Philippines.")
        sb.appendLine("A person facing an emergency asks: \"$query\"")
        profile.comprehensionHint()?.let { sb.appendLine(it) }
        sb.appendLine()

        // 2. The guidance as REFERENCE context. The model composes the answer FROM
        //    this, but may adapt it to the person's actual situation (grounded
        //    generation — not verbatim copy).
        sb.appendLine("Reference guidance from trusted sources:")
        chunks.forEach { c ->
            sb.appendLine()
            sb.appendLine("### ${c.title} (source: ${c.source})")
            sb.appendLine(c.text)
        }
        sb.appendLine()

        // 3. Personalization — concrete facts to weave in, not abstract rules.
        val personalLines = personalNotes(profile, relevantKeys, chunks)
        if (personalLines.isNotEmpty()) {
            sb.appendLine("Personal facts about this person (mention ONLY if the reference guidance")
            sb.appendLine("above directly involves them; never invent a connection):")
            personalLines.forEach { sb.appendLine("- $it") }
            sb.appendLine()
        }

        // 4. Output shape — grounded generation. The model answers the ACTUAL question
        //    using the reference guidance, adapting it when the situation differs
        //    (e.g. "I saw a snake" ≠ "I was bitten" → say how to stay safe, then what
        //    to do IF bitten). This fixes rigid verbatim-pack answers.
        if (history.isNotEmpty()) {
            sb.appendLine("Earlier in this chat (for context only):")
            history.forEach { (q, a) -> sb.appendLine("- They asked: \"$q\" — you answered: \"${a.take(160).replace('\n', ' ')}\"") }
            sb.appendLine()
        }
        sb.appendLine("SCOPE: you only help with emergencies, first aid, health and safety, disasters, survival,")
        sb.appendLine("and emergency contacts. If the person's message is about anything else (food or candy,")
        sb.appendLine("games, chit-chat, homework, shopping, money, jokes...), reply with exactly the single word")
        sb.appendLine("OFF_TOPIC and nothing else — even if the reference guidance above looks loosely related.")
        sb.appendLine("A message that merely mentions a disaster or emergency word but is not asking for safety")
        sb.appendLine("help or contacts (songs, movies, jokes, trivia, opinions) is also OFF_TOPIC.")
        sb.appendLine()
        sb.appendLine("Answer the person's actual question: \"$query\"")
        sb.appendLine("Base your answer on the reference guidance above. First check: does the")
        sb.appendLine("person's situation actually match the guidance? If they say something has")
        sb.appendLine("NOT happened (for example 'not bitten', 'not hurt'), do NOT give the treatment")
        sb.appendLine("steps for it. Instead reassure them in one sentence, give only the safety")
        sb.appendLine("steps that still apply, and say when to get help. Do not invent medical facts")
        sb.appendLine("beyond the guidance. Start with one short, calm, caring sentence, then give clear,")
        sb.appendLine("short numbered steps they can follow now (max 6). Use **bold** for the key action.")
        if (hasCritical) {
            sb.appendLine(
                if (lang == Lang.TL)
                    "If this is life-threatening, start with one short warning line (in Tagalog)."
                else
                    "If this is life-threatening, start with one short warning line."
            )
        }
        sb.appendLine("Do not repeat any line. Keep it concise.")

        // 5. Language instruction LAST (recency).
        sb.appendLine()
        if (lang == Lang.TL) {
            sb.appendLine("IMPORTANT: Write your entire answer in $langName only. Do not use English.")
        } else {
            sb.appendLine("Write your entire answer in $langName.")
        }
        return sb.toString().trimEnd()
    }

    /** Concrete, weave-in personalization notes (not abstract rules the model echoes). */
    private fun personalNotes(p: UserProfile, keys: Set<String>, chunks: List<Chunk>): List<String> = buildList {
        // Only surface allergies when the guidance itself involves medicine/food/etc.
        // Otherwise a small model invents irrelevant links (e.g. "seafood" in a snake answer).
        val medRelevant = Regex("allerg|aspirin|medic|gamot|drug|antibiotic|ointment|cream|food|drink|pagkain|inumin", RegexOption.IGNORE_CASE)
        if ("allergies" in keys && p.allergies.isNotEmpty() && chunks.any { medRelevant.containsMatchIn(it.text) })
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
