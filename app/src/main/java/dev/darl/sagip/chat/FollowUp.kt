package dev.darl.sagip.chat

/**
 * Detects short follow-up questions ("paano kung walang response?", "what if it's a child?")
 * that only make sense in the context of the earlier question, and builds the retrieval query
 * that keeps the conversation's topic.
 */
object FollowUp {
    private val CUES = Regex(
        "^(and|so|then|but|how about|what about|what if|how long|how often|how much|how many|how deep|how fast|how hard|" +
            "until when|why|bakit|paano kung|pano kung|e paano|eh paano|edi paano|paano naman|ano naman|ano pa|tapos|" +
            "gaano|ilang|hanggang kailan|dapat ba|kailangan ba|pwede ba|puwede ba)\\b" +
            "|\\b(it|this|that|him|her|them|ito|iyan|iyon|yan|yun|niya|nila|siya)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** A short message that points back at the previous topic. */
    fun looksLike(text: String): Boolean {
        val t = text.trim()
        return t.split(Regex("\\s+")).size <= 8 && CUES.containsMatchIn(t)
    }

    /**
     * The earlier user messages that carry the topic: walk back through consecutive follow-ups to the
     * question that started it (max 3 messages), then append the new message.
     */
    fun contextQuery(previousUserTexts: List<String>, text: String): String? {
        if (previousUserTexts.isEmpty()) return null
        var start = previousUserTexts.size - 1
        while (start > 0 && looksLike(previousUserTexts[start]) && previousUserTexts.size - start < 3) start--
        return (previousUserTexts.drop(start) + text).joinToString(" ")
    }
}
