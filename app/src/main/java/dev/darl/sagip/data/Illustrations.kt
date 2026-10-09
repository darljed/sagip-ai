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
class Illustrations(private val context: Context) {
    private val exts = listOf("png", "webp", "jpg", "jpeg")
    private val root: Set<String> by lazy { list("illustrations") }
    private val topicFiles: List<String> by lazy { list("illustrations/topics").sorted() }
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun list(dir: String): Set<String> =
        runCatching { context.assets.list(dir)?.toSet() }.getOrNull() ?: emptySet()

    /** Asset path of a category cover, or null. */
    fun categoryPath(categoryId: String): String? =
        exts.firstNotNullOfOrNull { e -> "$categoryId.$e".takeIf { it in root } }?.let { "illustrations/$it" }

    /** Hero image for a topic, or null. */
    fun heroPath(topic: String): String? =
        exts.firstNotNullOfOrNull { e -> "$topic.hero.$e".takeIf { it in topicFiles } }?.let { "illustrations/topics/$it" }

    /** Extra reference images (everything for this topic that is not the hero). */
    fun extraPaths(topic: String): List<String> =
        topicFiles.filter { f -> f.startsWith("$topic.") && !f.startsWith("$topic.hero.") && exts.any { f.endsWith(".$it") } }
            .map { "illustrations/topics/$it" }

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
