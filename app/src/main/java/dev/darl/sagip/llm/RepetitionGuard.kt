package dev.darl.sagip.llm

/**
 * Detects small-model repetition collapse, e.g. "-i-i-i-i-i..." or "the the the...".
 *
 * Heuristic over the tail window (cheap, runs per streamed token):
 *  - very few distinct chars in a long tail → a short-unit loop, or
 *  - the tail compresses to under a third of its length when a 1–3 char unit
 *    repeated 7+ times is collapsed.
 *
 * ponytail: a tail-window scan is enough — we only need to STOP the garbage, not
 * perfectly classify every degenerate sequence.
 */
internal fun looksRepetitive(text: String): Boolean {
    val n = text.length
    if (n < 40) return false
    val tail = text.substring(maxOf(0, n - 80))
    val distinct = tail.toSet().size
    if (distinct <= 3 && tail.length >= 40) return true
    val collapsed = tail.replace(Regex("(.{1,3}?)\\1{6,}"), "$1")
    if (collapsed.length < tail.length / 3) return true
    return looksPhraseRepetitive(text)
}

/**
 * Detects phrase/sentence-level looping, e.g. the model repeating the same clause
 * or step over and over ("Diyanan ang sugat... Diyanan ang sugat..."). The
 * char-level check above misses these because the character variety stays high.
 *
 * Heuristic: tokenise to words; if any 3–6 word shingle repeats 3+ times, or the
 * ratio of unique words to total words in a long text collapses, it's looping.
 */
internal fun looksPhraseRepetitive(text: String): Boolean {
    val words = Regex("\\p{L}+").findAll(text.lowercase()).map { it.value }.toList()
    if (words.size < 12) return false
    // Unique-word ratio: healthy guidance stays diverse; a loop tanks it.
    val uniqueRatio = words.toSet().size.toDouble() / words.size
    if (words.size >= 24 && uniqueRatio < 0.35) return true
    // Repeated shingle: a 3–5 word phrase appearing 3+ times.
    for (k in 5 downTo 3) {
        if (words.size < k * 3) continue
        val seen = HashMap<String, Int>()
        for (i in 0..words.size - k) {
            val gram = words.subList(i, i + k).joinToString(" ")
            val c = (seen[gram] ?: 0) + 1
            seen[gram] = c
            if (c >= 3) return true
        }
    }
    return false
}

/**
 * Trims a degenerate repeating tail (e.g. "...nasaktan at mag-i-i-i-i-i") back to
 * the last clean sentence/line, so the user sees the good prefix without the loop.
 */
internal fun trimRepetitionTail(text: String): String {
    // Collapse any short-unit run first (…-i-i-i… -> …-i).
    var s = text.replace(Regex("(.{1,3}?)\\1{3,}"), "$1")
    // Drop consecutive duplicate sentences/lines (phrase-level loops).
    val parts = s.split(Regex("(?<=[.\\n])"))
    val out = ArrayList<String>()
    for (p in parts) {
        val norm = p.trim().lowercase()
        val prev = out.lastOrNull()?.trim()?.lowercase()
        if (norm.isNotEmpty() && norm == prev) continue
        out.add(p)
    }
    s = out.joinToString("")
    // Cut back to the last sentence end or newline.
    val lastStop = s.lastIndexOfAny(charArrayOf('.', '\n'))
    if (lastStop > 20) s = s.substring(0, lastStop + 1)
    return s.trimEnd()
}


/**
 * Remove Gemma control tokens that sometimes leak into streamed text
 * (e.g. "<end_of_turn><end_of_turn>" when the model stops immediately).
 */
internal fun stripControlTokens(text: String): String =
    text.replace(Regex("</?(start_of_turn|end_of_turn|bos|eos|pad)>"), "").trim()


/**
 * Final-answer cleanup: drop control tokens, and cut off self-continuations where the
 * model starts a fake new turn after finishing ("\nResponse: ...", "\nAnswer: ...").
 */
internal fun cleanAnswer(text: String): String {
    var s = stripControlTokens(text)
    val m = Regex("\\n\\s*(Response|Answer|Sagot|Tugon)\\s*:").find(s)
    if (m != null && m.range.first > 20) s = s.substring(0, m.range.first)
    return s.trim()
}
