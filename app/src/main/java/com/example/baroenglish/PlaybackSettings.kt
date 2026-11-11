package com.example.baroenglish

import android.content.Context

enum class PlaybackMode {
    REPEAT_ONE,
    REPEAT_ALL,
    PLAY_ONCE
}

object PlaybackSettings {
    const val PREFS_NAME = "playback_settings"
    const val KEY_ENGLISH_ONLY = "english_only"
    private const val KEY_PLAYBACK_MODE = "playback_mode"

    fun isEnglishOnly(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENGLISH_ONLY, false)

    fun setEnglishOnly(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENGLISH_ONLY, enabled).apply()
    }

    fun getPlaybackMode(context: Context): PlaybackMode {
        val savedValue = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PLAYBACK_MODE, PlaybackMode.PLAY_ONCE.name)
        return PlaybackMode.values().firstOrNull { it.name == savedValue } ?: PlaybackMode.PLAY_ONCE
    }

    fun setPlaybackMode(context: Context, mode: PlaybackMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PLAYBACK_MODE, mode.name)
            .apply()
    }
}
