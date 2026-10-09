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
    return collapsed.length < tail.length / 3
}

/**
 * Trims a degenerate repeating tail (e.g. "...nasaktan at mag-i-i-i-i-i") back to
 * the last clean sentence/line, so the user sees the good prefix without the loop.
 */
internal fun trimRepetitionTail(text: String): String {
    // Collapse any short-unit run first (…-i-i-i… -> …-i).
    var s = text.replace(Regex("(.{1,3}?)\\1{3,}"), "$1")
    // Cut back to the last sentence end or newline before the collapse point.
    val lastStop = s.lastIndexOfAny(charArrayOf('.', '\n'))
    if (lastStop > 20) s = s.substring(0, lastStop + 1)
    return s.trimEnd()
}
