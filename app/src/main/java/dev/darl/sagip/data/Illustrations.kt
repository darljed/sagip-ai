package dev.darl.sagip.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

/**
 * Resolves illustration files shipped in `assets/illustrations/` (raster: png/webp/jpg).
 * The image set is produced separately and may change, so nothing is hardcoded:
 *
 *   illustrations/<category>.<ext>             category cover
 *   illustrations/topics/<topic>.hero.<ext>    topic hero (card + detail header)
 *   illustrations/topics/<topic>.<slot>.<ext>  extra reference images for that topic
 *
 * Anything missing simply returns null and the UI shows a placeholder slot.
 */
/** Parsed `illustrations/index.json` (pure, unit-testable). */
class IllustrationIndex(val covers: Map<String, String>, val steps: Map<String, Map<String, Int>>) {
    companion object {
        val EMPTY = IllustrationIndex(emptyMap(), emptyMap())
        fun parse(json: String): IllustrationIndex {
            val o = org.json.JSONObject(json)
            val c = o.optJSONObject("covers"); val st = o.optJSONObject("steps")
            return IllustrationIndex(
                covers = c?.keys()?.asSequence()?.associateWith { c.getString(it) } ?: emptyMap(),
                steps = st?.keys()?.asSequence()?.associateWith { t ->
                    st.getJSONObject(t).let { m -> m.keys().asSequence().associateWith { k -> m.getInt(k) } }
                } ?: emptyMap(),
            )
        }
    }
}

class Illustrations(private val context: Context) {
    private val index: IllustrationIndex by lazy {
        runCatching { IllustrationIndex.parse(context.assets.open("illustrations/index.json").bufferedReader().use { it.readText() }) }
            .getOrDefault(IllustrationIndex.EMPTY)
    }

    private val exts = listOf("png", "webp", "jpg", "jpeg")
    private val root: Set<String> by lazy { list("illustrations") }
    private val topicFiles: List<String> by lazy { list("illustrations/topics").sorted() }
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun list(dir: String): Set<String> =
        runCatching { context.assets.list(dir)?.toSet() }.getOrNull() ?: emptySet()

    /** Asset path of a category cover: its own file, else the hero of the topic chosen in index.json. */
    fun categoryPath(categoryId: String): String? =
        exts.firstNotNullOfOrNull { e -> "$categoryId.$e".takeIf { it in root } }?.let { "illustrations/$it" }
            ?: index.covers[categoryId]?.let { heroPath(it) }

    /** Hero image for a topic, or null. */
    fun heroPath(topic: String): String? =
        exts.firstNotNullOfOrNull { e -> "$topic.hero.$e".takeIf { it in topicFiles } }?.let { "illustrations/topics/$it" }

    private fun slotFile(topic: String, slot: String): String? =
        exts.firstNotNullOfOrNull { e -> "$topic.$slot.$e".takeIf { it in topicFiles } }?.let { "illustrations/topics/$it" }

    /** Images that illustrate a specific step: index.json mapping, or files named <topic>.step<N>.<ext>. */
    fun stepPaths(topic: String): Map<Int, String> {
        val out = LinkedHashMap<Int, String>()
        index.steps[topic]?.forEach { (slot, n) -> slotFile(topic, slot)?.let { out.putIfAbsent(n, it) } }
        Regex("^" + Regex.escape(topic) + "\\.step(\\d+)\\.").let { rx ->
            topicFiles.forEach { f -> rx.find(f)?.groupValues?.get(1)?.toIntOrNull()?.let { n -> out.putIfAbsent(n, "illustrations/topics/$f") } }
        }
        return out
    }

    /** Extra reference images: everything for this topic that is neither the hero nor mapped to a step. */
    fun extraPaths(topic: String): List<String> {
        val used = stepPaths(topic).values.toSet()
        return topicFiles.filter { f -> f.startsWith("$topic.") && !f.startsWith("$topic.hero.") && exts.any { f.endsWith(".$it") } }
            .map { "illustrations/topics/$it" }.filter { it !in used }
    }

    /** Decode (downsampled to ~[maxPx]) with a small LRU cache. Call off the main thread. */
    fun load(path: String, maxPx: Int = 900): Bitmap? {
        cache.get(path)?.let { return it }
        return runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (opts.outWidth / (sample * 2) >= maxPx) sample *= 2
            val real = BitmapFactory.Options().apply { inSampleSize = sample }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, real) }
        }.getOrNull()?.also { cache.put(path, it) }
    }
}
