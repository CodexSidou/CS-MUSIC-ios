package com.rst.player.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rst_prefs")

data class AppSettings(
    val themeMode: Int = THEME_DARK,
    val dynamicColor: Boolean = false,
    val autoplay: Boolean = true,
    val artistGridView: Boolean = true,
    val sleepMinutes: Int = 0,
    val startScreen: Int = START_HOME,
    val headphonePause: Boolean = true,
    val vibrantBackdrop: Boolean = true,
    val backgroundStyle: Int = BACKGROUND_AURORA,
    val totalListenMs: Long = 0L
) {
    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        const val THEME_OLED = 3
        const val START_HOME = 0
        const val START_LIBRARY = 1
        const val BACKGROUND_NONE = 0
        const val BACKGROUND_AURORA = 1
        const val BACKGROUND_WAVES = 2
        const val BACKGROUND_PARTICLES = 3
        const val BACKGROUND_PULSE = 4
    }
}

class UserPreferences(private val context: Context) {

    private object Keys {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AUTOPLAY = booleanPreferencesKey("autoplay")
        val ARTIST_GRID = booleanPreferencesKey("artist_grid")
        val SLEEP_MINUTES = intPreferencesKey("sleep_minutes")
        val START_SCREEN = intPreferencesKey("start_screen")
        val HEADPHONE_PAUSE = booleanPreferencesKey("headphone_pause")
        val VIBRANT_BACKDROP = booleanPreferencesKey("vibrant_backdrop")
        val BACKGROUND_STYLE = intPreferencesKey("background_style")
        val TOTAL_LISTEN_MS = longPreferencesKey("total_listen_ms")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE] ?: AppSettings.THEME_DARK,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            autoplay = prefs[Keys.AUTOPLAY] ?: true,
            artistGridView = prefs[Keys.ARTIST_GRID] ?: true,
            sleepMinutes = prefs[Keys.SLEEP_MINUTES] ?: 0,
            startScreen = prefs[Keys.START_SCREEN] ?: AppSettings.START_HOME,
            headphonePause = prefs[Keys.HEADPHONE_PAUSE] ?: true,
            vibrantBackdrop = prefs[Keys.VIBRANT_BACKDROP] ?: true,
            backgroundStyle = prefs[Keys.BACKGROUND_STYLE] ?: AppSettings.BACKGROUND_AURORA,
            totalListenMs = prefs[Keys.TOTAL_LISTEN_MS] ?: 0L
        )
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAutoplay(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTOPLAY] = enabled }
    }

    suspend fun setArtistGrid(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ARTIST_GRID] = enabled }
    }

    suspend fun setSleepMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.SLEEP_MINUTES] = minutes }
    }

    suspend fun setStartScreen(screen: Int) {
        context.dataStore.edit { it[Keys.START_SCREEN] = screen }
    }

    suspend fun setHeadphonePause(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HEADPHONE_PAUSE] = enabled }
    }

    suspend fun setVibrantBackdrop(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIBRANT_BACKDROP] = enabled }
    }

    suspend fun setBackgroundStyle(style: Int) {
        context.dataStore.edit { it[Keys.BACKGROUND_STYLE] = style }
    }

    suspend fun addListenTime(ms: Long) {
        if (ms <= 0) return
        context.dataStore.edit { it[Keys.TOTAL_LISTEN_MS] = (it[Keys.TOTAL_LISTEN_MS] ?: 0L) + ms }
    }
}
