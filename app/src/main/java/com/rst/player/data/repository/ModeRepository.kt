package com.rst.player.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rst.player.data.model.PlayerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val Context.modesDataStore: DataStore<Preferences> by preferencesDataStore(name = "rst_modes")

/**
 * Persists the app's play modes (Car / Gym / Sleep built-ins plus any
 * user-created ones) and which curated playlist each mode plays.
 */
class ModeRepository(context: Context) {

    private val appContext = context.applicationContext

    private object Keys {
        val MODES = stringPreferencesKey("modes")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _modes = MutableStateFlow(defaultModes())
    val modes: StateFlow<List<PlayerMode>> = _modes.asStateFlow()

    init {
        scope.launch {
            appContext.modesDataStore.data
                .map { prefs -> decode(prefs[Keys.MODES].orEmpty()) }
                .distinctUntilChanged()
                .collect { stored -> _modes.value = merge(stored) }
        }
    }

    suspend fun setPlaylist(modeId: String, playlistId: Long?) {
        appContext.modesDataStore.edit { prefs ->
            val list = decode(prefs[Keys.MODES].orEmpty())
            val current = list.firstOrNull { it.optString("id") == modeId }
                ?: JSONObject().put("id", modeId)
            current.remove("playlistId")
            if (playlistId != null) current.put("playlistId", playlistId)
            val merged = if (list.any { it.optString("id") == modeId }) {
                list.map { if (it.optString("id") == modeId) current else it }
            } else {
                list + current
            }
            prefs[Keys.MODES] = encode(merged)
        }
    }

    suspend fun addCustomMode(name: String, iconKey: String): PlayerMode {
        val id = "custom_${System.currentTimeMillis()}"
        val entry = JSONObject()
            .put("id", id)
            .put("name", name)
            .put("iconKey", iconKey)
            .put("builtin", false)
        appContext.modesDataStore.edit { prefs ->
            prefs[Keys.MODES] = encode(decode(prefs[Keys.MODES].orEmpty()) + entry)
        }
        return PlayerMode(id = id, name = name, iconKey = iconKey)
    }

    suspend fun removeMode(modeId: String) {
        appContext.modesDataStore.edit { prefs ->
            val list = decode(prefs[Keys.MODES].orEmpty())
                .filter { it.optString("id") != modeId }
            prefs[Keys.MODES] = encode(list)
        }
    }

    companion object {
        fun playlistNameFor(mode: PlayerMode): String =
            if (mode.isBuiltin) "${mode.name} Mode" else mode.name
    }

    private fun defaultModes(): List<PlayerMode> = listOf(
        PlayerMode(id = "car", name = "Car", iconKey = "car", isBuiltin = true),
        PlayerMode(id = "gym", name = "Gym", iconKey = "gym", isBuiltin = true),
        PlayerMode(id = "sleep", name = "Sleep", iconKey = "sleep", isBuiltin = true)
    )

    private fun merge(stored: List<JSONObject>): List<PlayerMode> {
        val byId = stored.associateBy { it.optString("id") }
        val result = defaultModes().map { def ->
            val saved = byId[def.id]
            val playlistId = saved?.optLong("playlistId", -1)?.takeIf { it > 0 }
            if (playlistId != null) def.copy(playlistId = playlistId) else def
        }.toMutableList()
        val builtinIds = result.map { it.id }.toSet()
        stored
            .filter { it.optString("id") !in builtinIds }
            .forEach { saved ->
                result.add(
                    PlayerMode(
                        id = saved.optString("id"),
                        name = saved.optString("name").ifBlank { "Mode" },
                        iconKey = saved.optString("iconKey").ifBlank { "star" },
                        isBuiltin = false,
                        playlistId = saved.optLong("playlistId", -1).takeIf { it > 0 }
                    )
                )
            }
        return result
    }

    private fun decode(raw: String): List<JSONObject> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        }.getOrDefault(emptyList())
    }

    private fun encode(list: List<JSONObject>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }
}
