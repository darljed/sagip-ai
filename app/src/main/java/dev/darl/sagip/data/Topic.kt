package dev.darl.sagip.data

import java.util.Locale

/**
 * A browsable guide: the EN + TL chunks of one (pack, topic) pair merged into a single
 * unit for the UI. Retrieval/chat still work on [Chunk]s; the UI works on [Topic]s.
 */
data class Topic(
    val id: String,            // "<pack>:<topic>" — unique even if a topic key repeats across packs
    val key: String,           // raw topic key (also the illustration file stem)
    val pack: String,
    val category: String,
    val severity: Severity,
    val callEmergency: Boolean,
    val tags: List<String>,
    val en: Chunk?,
    val tl: Chunk?,
) {
    fun chunk(lang: Lang): Chunk = (if (lang == Lang.TL) tl ?: en else en ?: tl)!!
    fun title(lang: Lang): String = chunk(lang).title
    fun source(lang: Lang): String = chunk(lang).source
    fun steps(lang: Lang): List<String> = parseSteps(chunk(lang).text)
    /** One-line card summary: the pack's own `summary` if present, else the first step. */
    fun summary(lang: Lang): String =
        chunk(lang).summary ?: steps(lang).firstOrNull()?.let { if (it.length > 90) it.take(87).trimEnd() + "…" else it }.orEmpty()

    companion object {
        private val STEP = Regex("^\\s*\\d+[.)]\\s*")

        /** "1. Do x\n2. Do y" -> ["Do x", "Do y"]; non-numbered text becomes one step. */
        fun parseSteps(text: String): List<String> {
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            return lines.map { it.replace(STEP, "") }
        }
    }
}

class TopicRepository(repo: PackRepository) {

    val topics: List<Topic> = repo.chunks
        .groupBy { it.pack to it.topic }
        .map { (k, cs) ->
            val first = cs.first()
            Topic(
                id = "${k.first}:${k.second}", key = k.second, pack = k.first,
                category = cs.firstNotNullOfOrNull { it.category } ?: k.first,
                severity = cs.maxOf { it.severity }, callEmergency = cs.any { it.callEmergency },
                tags = cs.flatMap { it.tags }.distinct(),
                en = cs.firstOrNull { it.lang == "en" } ?: first,
                tl = cs.firstOrNull { it.lang == "tl" } ?: first,
            )
        }

    private val byId = topics.associateBy { it.id }
    fun get(id: String): Topic? = byId[id]

    /** Map a retrieved chunk back to its topic. */
    fun forChunk(c: Chunk): Topic? = byId["${c.pack}:${c.topic}"]

    fun inCategory(category: String): List<Topic> = topics.filter { it.category == category }

    /**
     * Simple offline search across title (both languages), tags and steps.
     * Substring-based so Tagalog affixes still match ("lumilindol" ~ "lindol").
     */
    fun search(query: String, limit: Int = 40): List<Topic> {
        val terms = query.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }
        if (terms.isEmpty()) return emptyList()
        fun words(s: String) = s.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()
        return topics.map { t ->
            val titles = words(listOfNotNull(t.en?.title, t.tl?.title).joinToString(" "))
            val tags = words(t.tags.joinToString(" "))
            val body = words(listOfNotNull(t.en?.text, t.tl?.text).joinToString(" "))
            var s = 0
            for (q in terms) {
                if (stemHit(q, titles)) s += 6
                if (stemHit(q, tags)) s += 4
                if (q in body) s += 1
            }
            t to s
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }
}
