package dev.darl.sagip.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Minimal inline-markdown renderer for assistant text. Small models emit
 * `**bold**` (and sometimes stray `*`). We render `**x**` as bold and strip
 * leftover single `*`/`_` markers so the user never sees raw asterisks.
 *
 * ponytail: a hand-rolled bold pass is all the content needs — no markdown lib
 * for one feature. Upgrade path: swap in a real parser if we later need lists,
 * links, headings in-bubble.
 */
fun renderInlineMarkdown(src: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < src.length) {
        // Bold: **...**
        if (i + 1 < src.length && src[i] == '*' && src[i + 1] == '*') {
            val end = src.indexOf("**", i + 2)
            if (end > i + 1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(src.substring(i + 2, end))
                }
                i = end + 2
                continue
            }
        }
        // Strip stray markup chars the model sometimes leaks.
        val c = src[i]
        if (c == '*' || c == '`') { i++; continue }
        append(c)
        i++
    }
}
