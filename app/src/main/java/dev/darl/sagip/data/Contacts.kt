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
    /** Optional centre + radius so GPS works offline (no reverse geocoder needed). */
    val lat: Double? = null,
    val lng: Double? = null,
    val radiusKm: Double = 10.0,
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
        placeForGeo(t)?.let { geoPlace -> 
            // A named barangay in the same text refines the GPS area.
            val named = geoPlace.area.barangays.filter { b -> b.aliases.any { t.contains(it) } }.maxByOrNull { it.name.length }
            return Place(geoPlace.area, named)
        }
        val area = areas.firstOrNull { a -> a.aliases.any { t.contains(it) } }
            ?: areas.firstOrNull { a -> a.barangays.any { b -> b.aliases.any { t.contains(it) } } }
            ?: return null
        val b = area.barangays.filter { b -> b.aliases.any { t.contains(it) } }.maxByOrNull { it.name.length }
        return Place(area, b)
    }

    /** "geo:lat,lng" token (appended by the location lookup) -> nearest area within its radius. */
    fun placeForGeo(text: String): Place? {
        val m = Regex("geo:(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)").find(text) ?: return null
        val lat = m.groupValues[1].toDouble(); val lng = m.groupValues[2].toDouble()
        return areas.filter { it.lat != null && it.lng != null }
            .map { it to distanceKm(lat, lng, it.lat!!, it.lng!!) }
            .filter { (a, d) -> d <= a.radiusKm }
            .minByOrNull { it.second }?.first?.let { Place(it, null) }
    }

    /** Find areas/barangays by name for the Contacts search box (only places we have data for). */
    fun search(query: String, limit: Int = 8): List<Place> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.length < 2) return emptyList()
        val out = ArrayList<Place>()
        for (a in areas) {
            if (a.name.lowercase(Locale.ROOT).contains(q) || a.aliases.any { it.contains(q) }) out += Place(a, null)
            for (b in a.barangays) if (b.name.lowercase(Locale.ROOT).contains(q) || b.aliases.any { it.contains(q) }) out += Place(a, b)
        }
        return out.take(limit)
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
                        lat = if (a.has("lat")) a.getDouble("lat") else null,
                        lng = if (a.has("lng")) a.getDouble("lng") else null,
                        radiusKm = a.optDouble("radiusKm", 10.0),
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

/** Great-circle distance in km (haversine). */
fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(a))
}

/** Human label of a location-lookup result (drops the machine-readable geo token). */
fun placeLabel(raw: String): String = raw.substringBefore("geo:").trim().trimEnd(',')
