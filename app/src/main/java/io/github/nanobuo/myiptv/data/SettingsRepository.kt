package io.github.nanobuo.myiptv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val playlists: List<PlaylistSource> = emptyList(),
    val activePlaylistId: String? = null,
    val favorites: Set<String> = emptySet(),
    val lastChannelKey: String? = null,
    val invertUpDown: Boolean = false,
    val startOnLastChannel: Boolean = true,
    val autoUpdateCheck: Boolean = true,
    val preferredAudio: String = AUDIO_SYSTEM,
    val themeColor: String = THEME_DYNAMIC,
    val themeMode: String = MODE_DARK,
    /** Phones and tablets: continue in a small window when leaving the app during playback. */
    val pipEnabled: Boolean = true,
    /** Phones and tablets: keep the audio playing with the screen off or the app in background. */
    val backgroundAudio: Boolean = true,
    val lastUpdateCheck: Long = 0L,
) {
    val activePlaylist: PlaylistSource?
        get() = playlists.firstOrNull { it.id == activePlaylistId } ?: playlists.firstOrNull()

    companion object {
        const val AUDIO_SYSTEM = "system"
        const val AUDIO_ORIGINAL = "original"
        const val THEME_DYNAMIC = "dynamic"
        const val MODE_DARK = "dark"
        const val MODE_LIGHT = "light"
        const val MODE_SYSTEM = "system"
    }
}

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            playlists = p[PLAYLISTS]?.let { runCatching { json.decodeFromString<List<PlaylistSource>>(it) }.getOrNull() }
                ?: emptyList(),
            activePlaylistId = p[ACTIVE_PLAYLIST],
            favorites = p[FAVORITES] ?: emptySet(),
            lastChannelKey = p[LAST_CHANNEL],
            invertUpDown = p[INVERT_UP_DOWN] ?: false,
            startOnLastChannel = p[START_ON_LAST] ?: true,
            autoUpdateCheck = p[AUTO_UPDATE] ?: true,
            preferredAudio = p[PREFERRED_AUDIO] ?: AppSettings.AUDIO_SYSTEM,
            themeColor = p[THEME_COLOR] ?: AppSettings.THEME_DYNAMIC,
            themeMode = p[THEME_MODE] ?: AppSettings.MODE_DARK,
            pipEnabled = p[PIP_ENABLED] ?: true,
            backgroundAudio = p[BACKGROUND_AUDIO] ?: true,
            lastUpdateCheck = p[LAST_UPDATE_CHECK] ?: 0L,
        )
    }

    suspend fun savePlaylist(source: PlaylistSource) = context.dataStore.edit { p ->
        val list = decodePlaylists(p).toMutableList()
        val idx = list.indexOfFirst { it.id == source.id }
        if (idx >= 0) list[idx] = source else list += source
        p[PLAYLISTS] = json.encodeToString(list)
        if (p[ACTIVE_PLAYLIST] == null || idx < 0) p[ACTIVE_PLAYLIST] = source.id
    }

    suspend fun deletePlaylist(id: String) = context.dataStore.edit { p ->
        val list = decodePlaylists(p).filterNot { it.id == id }
        p[PLAYLISTS] = json.encodeToString(list)
        if (p[ACTIVE_PLAYLIST] == id) {
            list.firstOrNull()?.let { p[ACTIVE_PLAYLIST] = it.id } ?: p.remove(ACTIVE_PLAYLIST)
        }
    }

    suspend fun setActivePlaylist(id: String) = context.dataStore.edit { it[ACTIVE_PLAYLIST] = id }

    suspend fun toggleFavorite(key: String) = context.dataStore.edit { p ->
        val current = p[FAVORITES] ?: emptySet()
        p[FAVORITES] = if (key in current) current - key else current + key
    }

    suspend fun setLastChannel(key: String) = context.dataStore.edit { it[LAST_CHANNEL] = key }
    suspend fun setInvertUpDown(value: Boolean) = context.dataStore.edit { it[INVERT_UP_DOWN] = value }
    suspend fun setStartOnLastChannel(value: Boolean) = context.dataStore.edit { it[START_ON_LAST] = value }
    suspend fun setAutoUpdateCheck(value: Boolean) = context.dataStore.edit { it[AUTO_UPDATE] = value }
    suspend fun setPreferredAudio(value: String) = context.dataStore.edit { it[PREFERRED_AUDIO] = value }
    suspend fun setThemeColor(value: String) = context.dataStore.edit { it[THEME_COLOR] = value }
    suspend fun setPipEnabled(value: Boolean) = context.dataStore.edit { it[PIP_ENABLED] = value }
    suspend fun setBackgroundAudio(value: Boolean) = context.dataStore.edit { it[BACKGROUND_AUDIO] = value }
    suspend fun setThemeMode(value: String) = context.dataStore.edit { it[THEME_MODE] = value }
    suspend fun setLastUpdateCheck(value: Long) = context.dataStore.edit { it[LAST_UPDATE_CHECK] = value }

    private fun decodePlaylists(p: Preferences): List<PlaylistSource> =
        p[PLAYLISTS]?.let { runCatching { json.decodeFromString<List<PlaylistSource>>(it) }.getOrNull() } ?: emptyList()

    private companion object {
        val PLAYLISTS = stringPreferencesKey("playlists")
        val ACTIVE_PLAYLIST = stringPreferencesKey("active_playlist")
        val FAVORITES = stringSetPreferencesKey("favorites")
        val LAST_CHANNEL = stringPreferencesKey("last_channel")
        val INVERT_UP_DOWN = booleanPreferencesKey("invert_up_down")
        val START_ON_LAST = booleanPreferencesKey("start_on_last")
        val AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val PREFERRED_AUDIO = stringPreferencesKey("preferred_audio")
        val THEME_COLOR = stringPreferencesKey("theme_color")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PIP_ENABLED = booleanPreferencesKey("pip_enabled")
        val BACKGROUND_AUDIO = booleanPreferencesKey("background_audio")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
    }
}
