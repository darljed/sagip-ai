package dev.darl.sagip.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local, offline persistence for the [UserProfile]. SharedPreferences + JSON —
 * no new dependency (org.json is already available). The profile never leaves the
 * device; this is just on-disk storage so it survives app restarts.
 *
 * ponytail: a single JSON blob in prefs is plenty for one small object; no need
 * for DataStore/Room. Upgrade path: switch to DataStore if we add more state.
 */
class ProfileStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("sagip_profile", Context.MODE_PRIVATE)

    val isOnboarded: Boolean get() = prefs.getBoolean(KEY_ONBOARDED, false)

    fun load(): UserProfile? {
        val json = prefs.getString(KEY_PROFILE, null) ?: return null
        return runCatching { fromJson(json) }.getOrNull()
    }

    /** Persist the profile and mark onboarding complete. */
    fun save(profile: UserProfile) {
        prefs.edit()
            .putString(KEY_PROFILE, toJson(profile).toString())
            .putBoolean(KEY_ONBOARDED, true)
            .apply()
    }

    /** Mark onboarding complete even if the user skipped (empty profile saved). */
    fun markOnboarded() = prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_PROFILE = "profile_json"
        private const val KEY_ONBOARDED = "onboarded"

        fun toJson(p: UserProfile): JSONObject = JSONObject().apply {
            put("name", p.name)
            put("birthday", p.birthday)
            put("bloodType", p.bloodType)
            put("allergies", JSONArray(p.allergies))
            put("conditions", JSONArray(p.conditions))
            put("medications", JSONArray(p.medications))
            put("emergencyContactName", p.emergencyContactName)
            put("emergencyContactNumber", p.emergencyContactNumber)
            put("home", p.home)
            put("preferredLanguage", p.preferredLanguage.code)
            put("householdInfant", p.householdInfant)
            put("householdElderly", p.householdElderly)
            put("householdPwd", p.householdPwd)
            put("householdPregnant", p.householdPregnant)
        }

        fun fromJson(json: String): UserProfile {
            val o = JSONObject(json)
            fun strList(key: String): List<String> {
                val arr = o.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).map { arr.getString(it) }
            }
            return UserProfile(
                name = o.optString("name", ""),
                birthday = o.optString("birthday", ""),
                bloodType = o.optString("bloodType", ""),
                allergies = strList("allergies"),
                conditions = strList("conditions"),
                medications = strList("medications"),
                emergencyContactName = o.optString("emergencyContactName", ""),
                emergencyContactNumber = o.optString("emergencyContactNumber", ""),
                home = o.optString("home", ""),
                preferredLanguage = Lang.from(o.optString("preferredLanguage", "en")),
                householdInfant = o.optBoolean("householdInfant", false),
                householdElderly = o.optBoolean("householdElderly", false),
                householdPwd = o.optBoolean("householdPwd", false),
                householdPregnant = o.optBoolean("householdPregnant", false),
            )
        }
    }
}
