package dev.darl.sagip.data

import android.content.Context
import org.json.JSONArray

/**
 * Loads and holds the bundled emergency knowledge packs.
 *
 * JSON parsing is split out as a pure function ([parsePack]) so it can be unit-
 * tested on the plain JVM without a device/emulator — see scripts/ self-check.
 * Uses org.json (bundled in Android + available on JVM via json jar) — no new
 * dependency. ponytail: stdlib JSON is enough for 3 small fixed files.
 */
class PackRepository(val chunks: List<Chunk>) {

    val packNames: Set<String> get() = chunks.map { it.pack }.toSet()

    fun byLang(lang: Lang): List<Chunk> = chunks.filter { it.lang == lang.code }

    fun byPack(pack: String): List<Chunk> = chunks.filter { it.pack == pack }

    companion object {
        val PACK_FILES = listOf(
            "packs/first_aid.json",
            "packs/typhoon_flood.json",
            "packs/earthquake.json",
            "packs/fire.json",
            "packs/volcano.json",
            "packs/wildlife.json",
        )

        /** Load from Android assets (device/app runtime). */
        fun fromAssets(context: Context, files: List<String> = PACK_FILES): PackRepository {
            val all = files.flatMap { path ->
                val json = context.assets.open(path).bufferedReader().use { it.readText() }
                parsePack(json)
            }
            return PackRepository(all)
        }

        /**
         * Pure parser: pack JSON string -> list of Chunk. No Android dependency.
         * Throws if a required field is missing (fail loud, not silently drop).
         */
        fun parsePack(json: String): List<Chunk> {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Chunk(
                    id = o.getString("id"),
                    pack = o.getString("pack"),
                    topic = o.getString("topic"),
                    lang = o.getString("lang"),
                    title = o.getString("title"),
                    severity = Severity.from(o.getString("severity")),
                    tags = o.getJSONArray("tags").toStringList(),
                    text = o.getString("text"),
                    source = o.getString("source"),
                    personalize = o.getJSONArray("personalize").toStringList(),
                    callEmergency = o.getBoolean("call_emergency"),
                )
            }
        }

        private fun JSONArray.toStringList(): List<String> =
            (0 until length()).map { getString(it) }
    }
}
