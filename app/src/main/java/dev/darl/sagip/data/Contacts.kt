package dev.darl.sagip.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * A phone entry. [number] == null means "no number" and the entry is hidden. [sample] marks
 * MVP placeholder numbers, which the UI badges so nobody mistakes them for official ones.
 * [kind]: emergency | redcross | fire | police | hospital | ambulance | drrmo | barangay_hall | health | tanod
 */
data class ContactEntry(
    val kind: String,
    val label: String,
    val labelTl: String,
    val number: String?,
    val note: String = "",
    val sample: Boolean = false,
) {
    fun label(lang: Lang) = if (lang == Lang.TL) labelTl else label
}

data class Barangay(val name: String, val aliases: List<String>, val contacts: List<ContactEntry>)

data class Area(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val contacts: List<ContactEntry>,
    val barangays: List<Barangay>,
)

/** A resolved place: an area and (optionally) a barangay inside it. */
data class Place(val area: Area, val barangay: Barangay?) {
    val title: String get() = if (barangay != null) "${barangay.name}, ${area.name}" else area.name
}

class ContactDirectory(val national: List<ContactEntry>, val areas: List<Area>) {

    /** Best-effort match of free text (profile home, GPS address) to a known area/barangay. */
    fun placeFor(text: String): Place? {
        val t = text.lowercase(Locale.ROOT)
        if (t.isBlank()) return null
        val area = areas.firstOrNull { a -> a.aliases.any { t.contains(it) } }
            ?: areas.firstOrNull { a -> a.barangays.any { b -> b.aliases.any { t.contains(it) } } }
            ?: return null
        val b = area.barangays.filter { b -> b.aliases.any { t.contains(it) } }.maxByOrNull { it.name.length }
        return Place(area, b)
    }

    /** Local contacts that actually have numbers: barangay first, then the city/area. */
    fun localContacts(place: Place?): List<ContactEntry> =
        if (place == null) emptyList()
        else ((place.barangay?.contacts ?: emptyList()) + place.area.contacts).filter { it.number != null }

    /** Contacts of the given kinds near [place]; national ones (911, Red Cross) match by kind too. */
    fun find(kinds: Set<String>, place: Place?): List<ContactEntry> =
        (localContacts(place) + national).filter { it.kind in kinds && it.number != null }

    companion object {
        private fun entries(a: JSONArray?): List<ContactEntry> = (0 until (a?.length() ?: 0)).map { i ->
            val c = a!!.getJSONObject(i)
            ContactEntry(
                kind = c.optString("kind", "other"),
                label = c.getString("label"),
                labelTl = c.optString("labelTl", c.getString("label")),
                number = if (c.isNull("number")) null else c.getString("number").ifBlank { null },
                note = c.optString("note"),
                sample = c.optBoolean("sample", false),
            )
        }

        private fun strings(a: JSONArray?): List<String> = (0 until (a?.length() ?: 0)).map { a!!.getString(it).lowercase(Locale.ROOT) }

        fun parse(json: String): ContactDirectory {
            val o = JSONObject(json)
            val areas = o.optJSONArray("areas")
            return ContactDirectory(
                national = entries(o.optJSONArray("national")),
                areas = (0 until (areas?.length() ?: 0)).map { i ->
                    val a = areas!!.getJSONObject(i)
                    val bs = a.optJSONArray("barangays")
                    Area(
                        id = a.getString("id"), name = a.getString("name"),
                        aliases = strings(a.optJSONArray("aliases")).ifEmpty { listOf(a.getString("name").lowercase(Locale.ROOT)) },
                        contacts = entries(a.optJSONArray("contacts")),
                        barangays = (0 until (bs?.length() ?: 0)).map { j ->
                            val b = bs!!.getJSONObject(j)
                            Barangay(b.getString("name"), strings(b.optJSONArray("aliases")).ifEmpty { listOf(b.getString("name").lowercase(Locale.ROOT)) }, entries(b.optJSONArray("contacts")))
                        },
                    )
                },
            )
        }

        fun fromAssets(context: Context): ContactDirectory =
            parse(context.assets.open("contacts.json").bufferedReader().use { it.readText() })
    }
}
