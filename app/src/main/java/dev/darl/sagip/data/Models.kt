package dev.darl.sagip.data

/**
 * One retrievable unit of emergency guidance. Mirrors docs/CHUNK-SCHEMA.md exactly.
 */
data class Chunk(
    val id: String,
    val pack: String,          // first_aid | typhoon_flood | earthquake
    val topic: String,
    val lang: String,          // "en" | "tl"
    val title: String,
    val severity: Severity,
    val tags: List<String>,    // bilingual retrieval keywords
    val text: String,          // numbered steps
    val source: String,        // named authority (Red Cross, DOH, NDRRMC, WHO, PHIVOLCS, PAGASA, BFP)
    val personalize: List<String>, // profile keys that may modify this chunk
    val callEmergency: Boolean,
    /** Optional (schema v2): UI category id; falls back to the pack id. */
    val category: String? = null,
    /** Optional (schema v2): one-line card summary. */
    val summary: String? = null,
)

enum class Severity(val key: String) {
    INFO("info"),
    CAUTION("caution"),
    URGENT("urgent"),
    CRITICAL("critical");

    companion object {
        fun from(key: String): Severity =
            entries.firstOrNull { it.key == key } ?: INFO
    }
}

enum class Lang(val code: String) {
    EN("en"), TL("tl");

    companion object {
        fun from(code: String): Lang = if (code == "tl") TL else EN
    }
}

/**
 * Locally-stored user profile. Never leaves the device. Mirrors CHUNK-SCHEMA.md §3.
 * All fields optional so onboarding can be partial / skipped.
 */
data class UserProfile(
    val name: String = "",
    val birthday: String = "",   // ISO yyyy-MM-dd, empty if unknown
    val bloodType: String = "",
    val allergies: List<String> = emptyList(),
    val conditions: List<String> = emptyList(),
    val medications: List<String> = emptyList(),
    val emergencyContactName: String = "",
    val emergencyContactNumber: String = "",
    val home: String = "",
    val preferredLanguage: Lang = Lang.EN,
    val householdInfant: Boolean = false,
    val householdElderly: Boolean = false,
    val householdPwd: Boolean = false,
    val householdPregnant: Boolean = false,
) {
    val hasEmergencyContact: Boolean
        get() = emergencyContactName.isNotBlank() && emergencyContactNumber.isNotBlank()

    /** Age in years from [birthday] (ISO yyyy-MM-dd), or null if unknown/unparseable. */
    val age: Int?
        get() = runCatching {
            val parts = birthday.split("-")
            if (parts.size != 3) return null
            val (y, m, d) = parts.map { it.toInt() }
            val now = java.util.Calendar.getInstance()
            var a = now.get(java.util.Calendar.YEAR) - y
            val mm = now.get(java.util.Calendar.MONTH) + 1
            val dd = now.get(java.util.Calendar.DAY_OF_MONTH)
            if (mm < m || (mm == m && dd < d)) a--
            if (a in 0..120) a else null
        }.getOrNull()

    /**
     * Comprehension level used to tune answer complexity (requested: gauge the
     * user's understanding by age). Children and the very elderly get simpler
     * wording; everyone else gets normal guidance.
     */
    fun comprehensionHint(): String? = when (val a = age) {
        null -> null
        in 0..12 -> "The user is a child ($a). Use very simple words and short sentences."
        in 13..17 -> "The user is a teenager ($a). Use clear, simple language."
        in 65..120 -> "The user is a senior ($a). Use clear, calm, simple wording and larger steps."
        else -> null
    }

    /** True household flags, as human-readable labels for prompt injection. */
    fun householdLabels(): List<String> = buildList {
        if (householdInfant) add("infant")
        if (householdElderly) add("elderly")
        if (householdPwd) add("person with disability")
        if (householdPregnant) add("pregnant member")
    }

    companion object {
        /** A realistic demo profile — used for mock-data development + the demo script. */
        val DEMO = UserProfile(
            name = "Juan Dela Cruz",
            birthday = "1990-06-15",
            bloodType = "O+",
            allergies = listOf("penicillin"),
            conditions = listOf("asthma"),
            medications = listOf("salbutamol inhaler"),
            emergencyContactName = "Maria",
            emergencyContactNumber = "+639171234567",
            home = "Barangay San Roque, San Pablo City",
            preferredLanguage = Lang.TL,
            householdInfant = true,
        )
    }
}
