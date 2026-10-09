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

        // Collapse to the best score per topic (EN+TL twins both score; keep the max).
        val bestByTopic = LinkedHashMap<String, Pair<Chunk, Int>>()
        for ((chunk, sc) in scored) {
            val cur = bestByTopic[chunk.topic]
            if (cur == null || sc > cur.second) bestByTopic[chunk.topic] = chunk to sc
        }
        val ranked = bestByTopic.values.sortedByDescending { it.second }

        // Absolute relevance floor: a real match lands a tag hit (5) or topic hit (4);
        // incidental body-word overlap (e.g. a falling-tree query grazing "puno"/"dapat"
        // in the flood chunk) scores ~2. If even the TOP hit is below MIN_SCORE, we have
        // no genuine guidance — return empty so the UI shows a safe "call 911" fallback
        // instead of confidently rendering the wrong pack. Critical for an emergency app.
        if (ranked.first().second < MIN_SCORE) return emptyList()

        // Relevance gate: a secondary topic rides along ONLY if it's nearly as strong
        // as the top hit (>= RELEVANCE_RATIO of it). This makes a dominant match return
        // ALONE — important for a small (1B) model, which gets confused and starts
        // chanting when fed two topics' step-lists at once (e.g. earthquake during+after
        // -> "Duck! Cover! Hold!" loop). Genuinely co-equal topics still both pass.
        val topScore = ranked.first().second
        val threshold = topScore * RELEVANCE_RATIO
        val kept = ranked.filter { it.second >= threshold }.take(k)

        // Resolve each kept topic to the twin in the requested language, else EN.
        return kept.map { (chunk, _) ->
            repo.chunks.firstOrNull {
                it.topic == chunk.topic && it.pack == chunk.pack && it.lang == lang.code
            } ?: chunk
        }
    }

    /** Overlap score: tag hits weigh most, then topic, title, body. */
    private fun score(c: Chunk, terms: Set<String>): Int {
        val tagSet = c.tags.flatMap { tokenize(it) }.toSet()
        val topicSet = tokenize(c.topic.replace("_", " "))
        val titleSet = tokenize(c.title)
        val bodySet = tokenize(c.text)
        var s = 0
        for (t in terms) {
            if (hit(t, tagSet)) s += 5
            if (hit(t, topicSet)) s += 4
            if (hit(t, titleSet)) s += 2
            if (hit(t, bodySet)) s += 1
        }
        return s
    }

    /**
     * A query term "hits" a pool word on exact match OR substring containment either
     * way, provided the shorter token is >= 4 chars. This is a cheap stemmer: Tagalog
     * conjugations/affixes share a stem with the tag (lumi-LINDOL -> "lindol",
     * ma-KURYENTE -> "kuryente", nag-yayanig -> "yanig"), which exact whole-word
     * matching misses. The length guard stops short tokens from false-matching.
     *
     * ponytail: substring is a poor-man's stemmer; the real upgrade is a proper
     * stemmer or EmbeddingGemma semantic retrieval, but this fixes the common
     * conjugation misses for ~zero cost.
     */
    private fun hit(term: String, pool: Set<String>): Boolean = stemHit(term, pool)

    private fun tokenize(s: String): Set<String> =
        s.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 && it !in STOPWORDS }
            .toSet()

    companion object {
        // A secondary topic rides along only if it scores >= 80% of the top hit.
        // High on purpose: a small model does best with ONE focused topic.
        private const val RELEVANCE_RATIO = 0.8
        // Absolute floor for the top hit. A genuine match scores >= 4 (one topic hit)
        // or >= 5 (one tag hit); spurious body-only overlap scores ~1–2. Below this =
        // "no guidance for this query" → UI shows the safe 911 fallback.
        private const val MIN_SCORE = 4
        // Minimal EN+TL stopwords so short function words don't create noise.
        private val STOPWORDS = setOf(
            "the", "and", "for", "are", "was", "with", "what", "how", "when", "who",
            "ang", "ng", "sa", "na", "ko", "ba", "ano", "may", "mga", "ay", "si",
            "do", "to", "is", "it", "my", "me", "in", "on", "of", "at", "a", "an",
            // chit-chat fillers that stem-match real tags ("kailangan" ~ "kailan lumikas")
            "kailangan", "gusto", "pwede", "puwede", "pahingi", "please", "paki", "pakiusap", "ayoko", "want", "need", "give"
        )
    }
}

/**
 * A query term hits a pool word on exact match or stem containment (cheap Tagalog
 * stemmer: lumi-LINDOL ~ lindol). 4-letter stems must sit at a word edge so
 * "nahi-MATA-y" (fainted) does not match "mata" (eye).
 */
internal fun stemHit(term: String, pool: Set<String>): Boolean {
    if (term in pool) return true
    return pool.any { w ->
        val shorter = minOf(term.length, w.length)
        when {
            shorter >= 5 -> term.contains(w) || w.contains(term)
            shorter == 4 -> term.startsWith(w) || term.endsWith(w) || w.startsWith(term) || w.endsWith(term)
            else -> false
        }
    }
}
