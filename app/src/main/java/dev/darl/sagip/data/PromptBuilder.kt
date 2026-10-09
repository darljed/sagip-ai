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
        followUp: Boolean = false,
        /** Offices (label to number), e.g. the local DRRMO. Offered to the model as optional, never forced. */
        offices: List<Pair<String, String>> = emptyList(),
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
        val first = profile.name.trim().substringBefore(' ')
        if (first.isNotBlank()) {
            sb.appendLine("The person's name is $first. Address them by name ($first) in your first sentence.")
            sb.appendLine("Never call them 'Mahal', 'dear', 'honey', 'friend', 'kaibigan', 'iho' or any other pet name.")
        } else {
            sb.appendLine("Do not use pet names such as 'Mahal', 'dear' or 'honey'.")
        }
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
        if (isPersonalQuestion(query)) {
            // The person is asking about THEIR OWN allergies / food / medicine / health: hand the model the
            // whole saved profile and tell it to use it. (The gated notes below hide these facts otherwise.)
            sb.appendLine("About this person (accurate, from their saved profile):")
            profileFacts(profile).forEach { sb.appendLine("- $it") }
            sb.appendLine("The question is about this person's own health, so USE these facts directly: name their")
            sb.appendLine("allergies, conditions or medicines and what to avoid or watch for. Never say you do not")
            sb.appendLine("know them when they are listed above. If a fact says 'none saved', say it is not saved")
            sb.appendLine("yet and that they can add it in Settings.")
            sb.appendLine()
        } else if (personalLines.isNotEmpty()) {
            sb.appendLine("Personal facts about this person (mention ONLY if the reference guidance")
            sb.appendLine("above directly involves them; never invent a connection):")
            personalLines.forEach { sb.appendLine("- $it") }
            sb.appendLine()
        }

        // 3b. Optional contacts: the model decides whether outside help is warranted.
        val disaster = isDisaster(chunks)
        val offerContacts = chunks.any { it.callEmergency } || disaster
        val person = if (offerContacts && profile.hasEmergencyContact) profile.emergencyContactName to profile.emergencyContactNumber else null
        if (person != null || (offerContacts && offices.isNotEmpty())) {
            if (disaster && person != null) {
                sb.appendLine("This is a disaster that can affect family. After the safety steps, add ONE short final line telling")
                sb.appendLine("them to contact ${person.first} (${person.second}) to make sure their family and household are safe.")
                sb.appendLine()
            }
            sb.appendLine("Contacts you MAY suggest calling — only if this situation is serious enough that the person needs")
            sb.appendLine("someone to come or help, and at most one or two. If it is not needed, do not mention any contact")
            sb.appendLine("or phone number at all:")
            person?.let { (n, num) -> sb.appendLine("- ${n} ($num) — a PERSON, their emergency contact") }
            if (offerContacts) offices.forEach { (n, num) -> sb.appendLine("- $n ($num) — an office") }
            if (lang == Lang.TL) sb.appendLine("Grammar: for a person say 'tawagan si ${person?.first ?: "Name"}' or 'tumawag kay ${person?.first ?: "Name"}', never 'sa ${person?.first ?: "Name"}'. Use 'sa' only for offices and places.")
            sb.appendLine()
        }

        // 4. Output shape — grounded generation. The model answers the ACTUAL question
        //    using the reference guidance, adapting it when the situation differs
        //    (e.g. "I saw a snake" ≠ "I was bitten" → say how to stay safe, then what
        //    to do IF bitten). This fixes rigid verbatim-pack answers.
        if (history.isNotEmpty()) {
            sb.appendLine("Earlier in this chat:")
            history.forEach { (q, a) -> sb.appendLine("- They asked: \"$q\" — you answered: \"${a.take(420).replace('\n', ' ')}\"") }
            sb.appendLine("If the new question follows up on this chat (it refers back to the same situation), answer that")
            sb.appendLine("specific point first — yes or no with the reason — using the earlier answer and the guidance, then")
            sb.appendLine("add only what is new. Do not repeat the whole guide.")
            sb.appendLine()
        }
        sb.appendLine("SCOPE: you only help with emergencies, first aid, health and safety, disasters, survival,")
        sb.appendLine("and emergency contacts. If the person's message is about anything else (food or candy,")
        sb.appendLine("games, chit-chat, homework, shopping, money, jokes...), reply with exactly the single word")
        sb.appendLine("OFF_TOPIC and nothing else — even if the reference guidance above looks loosely related.")
        sb.appendLine("Questions about the person's own allergies, foods or medicines to avoid, conditions or health are ON topic.")
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
        if (followUp && history.isNotEmpty()) {
            sb.appendLine("This is a FOLLOW-UP to the earlier chat. Do NOT repeat the earlier steps. In 1-2 sentences answer")
            sb.appendLine("exactly what they asked (begin with yes or no if it is a yes/no question). Then give at most 3 short")
            sb.appendLine("numbered steps, only if they add something new. Use **bold** for the key action.")
        } else {
            sb.appendLine("short numbered steps they can follow now (max 6). Use **bold** for the key action.")
        }
        if (hasCritical) {
            sb.appendLine(
                if (lang == Lang.TL)
                    "If this is life-threatening, start with one short warning line (in Tagalog)."
                else
                    "If this is life-threatening, start with one short warning line."
            )
        }
        sb.appendLine("Do not repeat any line. Keep it concise. Do not end with a generic 'call 911 / call the barangay' line;")
        sb.appendLine("mention calling for help only when this situation truly needs someone to come or advise.")

        // 5. Language instruction LAST (recency).
        sb.appendLine()
        if (lang == Lang.TL) {
            sb.appendLine("IMPORTANT: Write your entire answer in $langName only. Do not use English.")
        } else {
            sb.appendLine("Write your entire answer in $langName.")
        }
        return sb.toString().trimEnd()
    }

    private val DISASTER_PACKS = setOf("typhoon_flood", "earthquake", "volcano", "fire")

    /** Disaster guides (flood, earthquake, volcano, fire): the family should be checked on. */
    fun isDisaster(chunks: List<Chunk>) = chunks.any { it.pack in DISASTER_PACKS }

    /** Questions about the person's own allergies, food, medicine or health (EN + Tagalog). */
    private val PERSONAL_Q = Regex(
        "allerg|alerhi|alerji|irritat|\\bavoid\\b|iwasan|\\bfoods?\\b|\\beat\\b|kainin|pagkain|gamot|medicine|medication|\\bmeds\\b|" +
            "\\bfor me\\b|safe for me|\\bmy (age|condition|conditions|health|blood|allergies)\\b|\\bako\\b.*\\b(ligtas|bawal)\\b|bawal",
        RegexOption.IGNORE_CASE,
    )

    fun isPersonalQuestion(query: String) = PERSONAL_Q.containsMatchIn(query)

    /** Every saved profile fact, with an explicit 'none saved' so the model never guesses. */
    fun profileFacts(p: UserProfile): List<String> = buildList {
        add("Age: ${p.age?.toString() ?: "not saved"}")
        add("Allergies: ${p.allergies.joinToString(", ").ifBlank { "none saved" }}")
        add("Medical conditions: ${p.conditions.joinToString(", ").ifBlank { "none saved" }}")
        add("Medications: ${p.medications.joinToString(", ").ifBlank { "none saved" }}")
        add("Blood type: ${p.bloodType.ifBlank { "not saved" }}")
        p.householdLabels().takeIf { it.isNotEmpty() }?.let { add("Household includes: ${it.joinToString(", ")}") }
    }

    /** Concrete, weave-in personalization notes (not abstract rules the model echoes). */
    private fun personalNotes(p: UserProfile, keys: Set<String>, chunks: List<Chunk>): List<String> = buildList {
        // Only surface allergies when the guidance itself involves medicine/food/etc.
        // Otherwise a small model invents irrelevant links (e.g. "seafood" in a snake answer).
        val medRelevant = Regex("allerg|aspirin|medic|gamot|drug|antibiotic|ointment|cream|food|drink|pagkain|inumin", RegexOption.IGNORE_CASE)
        if ("allergies" in keys && p.allergies.isNotEmpty() && chunks.any { medRelevant.containsMatchIn(it.text) })
            add("Allergic to ${p.allergies.joinToString(", ")} — warn if any step risks this.")
        p.age?.let { add("Age $it — adapt advice to their age; do not mention it unless relevant.") }
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
    }
}
