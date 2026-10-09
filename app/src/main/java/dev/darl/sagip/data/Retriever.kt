package dev.darl.sagip.data

import java.util.Locale

/**
 * Retrieves the most relevant guidance chunks for a user query.
 *
 * This interface is the seam between CAG (keyword/section lookup, [KeywordRetriever])
 * and full RAG (embeddings + vector similarity, dropped in later with EmbeddingGemma).
 * The prompt builder and chat flow depend only on this interface, so swapping the
 * implementation is a one-line change — exactly the H7 safety valve in PLAN.md.
 */
interface Retriever {
    fun retrieve(query: String, lang: Lang, k: Int = 4): List<Chunk>
}

/**
 * CAG retriever: scores chunks by keyword overlap against tags/topic/title/text.
 * No embeddings, no model — fully testable offline, instant, memory-cheap.
 *
 * ponytail: for a small fixed emergency corpus (32 chunks) keyword+section scoring
 * is plenty; semantic RAG is an upgrade, not a requirement. Known ceiling: it won't
 * match paraphrases with zero shared words (e.g. "my arm snapped" vs tag "fracture")
 * — the bilingual tag lists are the mitigation; EmbeddingGemma RAG is the upgrade path.
 */
class KeywordRetriever(private val repo: PackRepository) : Retriever {

    override fun retrieve(query: String, lang: Lang, k: Int): List<Chunk> {
        val terms = tokenize(query)
        if (terms.isEmpty()) return emptyList()

        // Score the full corpus (any language) so a Tagalog query can still match an
        // English chunk's bilingual tags, then prefer the requested language per topic.
        val scored = repo.chunks
            .map { it to score(it, terms) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

        if (scored.isEmpty()) return emptyList()

        // Resolve to the requested language: for each matched topic, return the twin
        // in `lang` if it exists, else fall back to the matched chunk (EN).
        val seenTopics = LinkedHashSet<String>()
        val result = ArrayList<Chunk>(k)
        for ((chunk, _) in scored) {
            if (chunk.topic in seenTopics) continue
            val preferred = repo.chunks.firstOrNull {
                it.topic == chunk.topic && it.pack == chunk.pack && it.lang == lang.code
            } ?: chunk
            result.add(preferred)
            seenTopics.add(chunk.topic)
            if (result.size >= k) break
        }
        return result
    }

    /** Overlap score: tag hits weigh most, then topic, title, body. */
    private fun score(c: Chunk, terms: Set<String>): Int {
        val tagSet = c.tags.flatMap { tokenize(it) }.toSet()
        val topicSet = tokenize(c.topic.replace("_", " "))
        val titleSet = tokenize(c.title)
        val bodySet = tokenize(c.text)
        var s = 0
        for (t in terms) {
            if (t in tagSet) s += 5
            if (t in topicSet) s += 4
            if (t in titleSet) s += 2
            if (t in bodySet) s += 1
        }
        return s
    }

    private fun tokenize(s: String): Set<String> =
        s.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 && it !in STOPWORDS }
            .toSet()

    companion object {
        // Minimal EN+TL stopwords so short function words don't create noise.
        private val STOPWORDS = setOf(
            "the", "and", "for", "are", "was", "with", "what", "how", "when", "who",
            "ang", "ng", "sa", "na", "ko", "ba", "ano", "may", "mga", "ay", "si",
            "do", "to", "is", "it", "my", "me", "in", "on", "of", "at", "a", "an"
        )
    }
}
