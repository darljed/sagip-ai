package dev.darl.sagip.data

import android.content.Context
import dev.darl.sagip.ui.theme.ThemeMode

/** Small app-level preferences that are not part of the user's profile. */
class SettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("sagip_settings", Context.MODE_PRIVATE)

    /** Send a voice message automatically once the speaker stops (default on). */
    var voiceAutoSend: Boolean
        get() = sp.getBoolean("voice_autosend", true)
        set(v) { sp.edit().putBoolean("voice_autosend", v).apply() }

    fun clear() = sp.edit().clear().apply()

    var themeMode: ThemeMode
        get() = ThemeMode.from(sp.getString("theme", null))
        set(v) { sp.edit().putString("theme", v.key).apply() }
}
