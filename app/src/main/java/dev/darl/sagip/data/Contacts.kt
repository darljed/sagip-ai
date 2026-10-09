package dev.darl.sagip.data

import android.content.Context
import org.json.JSONObject

/** A phone entry. [number] == null means "not added yet" (MVP placeholder). */
data class ContactEntry(val label: String, val labelTl: String, val number: String?, val note: String = "") {
    fun label(lang: Lang) = if (lang == Lang.TL) labelTl else label
}

data class BarangayContacts(val name: String, val contacts: List<ContactEntry>)

data class ContactDirectory(
    val national: List<ContactEntry>,
    val city: String,
    val cityContacts: List<ContactEntry>,
    val barangays: List<BarangayContacts>,
) {
    /** Best-effort match of the profile's free-text home against barangay names. */
    fun barangayFor(home: String): BarangayContacts? {
        val h = home.lowercase()
        return barangays.filter { h.contains(it.name.lowercase()) }.maxByOrNull { it.name.length }
    }

    companion object {
        fun parse(json: String): ContactDirectory {
            val o = JSONObject(json)
            fun list(a: org.json.JSONArray?) = (0 until (a?.length() ?: 0)).map { i ->
                val c = a!!.getJSONObject(i)
                ContactEntry(
                    label = c.getString("label"),
                    labelTl = c.optString("labelTl", c.getString("label")),
                    number = if (c.isNull("number")) null else c.getString("number").ifBlank { null },
                    note = c.optString("note"),
                )
            }
            val brgy = o.optJSONArray("barangays")
            return ContactDirectory(
                national = list(o.optJSONArray("national")),
                city = o.optString("city"),
                cityContacts = list(o.optJSONArray("cityContacts")),
                barangays = (0 until (brgy?.length() ?: 0)).map { i ->
                    val b = brgy!!.getJSONObject(i)
                    BarangayContacts(b.getString("name"), list(b.optJSONArray("contacts")))
                },
            )
        }

        fun fromAssets(context: Context): ContactDirectory =
            parse(context.assets.open("contacts.json").bufferedReader().use { it.readText() })
    }
}
