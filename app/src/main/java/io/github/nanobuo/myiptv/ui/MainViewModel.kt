package io.github.nanobuo.myiptv.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.nanobuo.myiptv.MyIptvApp
import io.github.nanobuo.myiptv.data.AppSettings
import io.github.nanobuo.myiptv.data.Channel
import io.github.nanobuo.myiptv.data.EpgData
import io.github.nanobuo.myiptv.data.Playlist
import io.github.nanobuo.myiptv.data.PlaylistSource
import io.github.nanobuo.myiptv.update.UpdateInfo
import io.github.nanobuo.myiptv.update.UpdateStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Welcome : Screen
    data object Home : Screen
    data object Player : Screen
    data object Settings : Screen
    data object Guide : Screen
    data class PlaylistEditor(val playlistId: String?) : Screen
}

data class UiState(
    val playlistLoading: Boolean = false,
    val epgLoading: Boolean = false,
    val playlistError: String? = null,
    val epgError: String? = null,
    val channels: List<Channel> = emptyList(),
    val groups: List<String> = emptyList(),
    val epg: EpgData = EpgData.EMPTY,
    val currentKey: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as MyIptvApp).container
    private val repo = container.playlists
    private val settingsRepo = container.settings
    val player = container.player
    val updateManager = container.updateManager

    val settings: StateFlow<AppSettings?> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val updateStatus: StateFlow<UpdateStatus> = updateManager.status

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    /** Navigation back stack; empty until settings are loaded. */
    val backStack = mutableStateListOf<Screen>()

    private var loadJob: Job? = null
    private var autoStartDone = false
    private var previousKey: String? = null

    init {
        viewModelScope.launch {
            val s = settings.filterNotNull().first()
            player.preferredAudio = s.preferredAudio
            val active = s.activePlaylist
            if (active == null) {
                backStack.add(Screen.Welcome)
            } else {
                backStack.add(Screen.Home)
                load(active, refresh = true)
            }
            if (s.autoUpdateCheck && System.currentTimeMillis() - s.lastUpdateCheck > UPDATE_CHECK_INTERVAL) {
                settingsRepo.setLastUpdateCheck(System.currentTimeMillis())
                if (updateManager.check() is UpdateStatus.Available) _showUpdateDialog.value = true
            }
        }
    }

    // ---- Navigation -------------------------------------------------------------------------

    val currentScreen: Screen? get() = backStack.lastOrNull()

    fun navigate(screen: Screen) {
        val existing = backStack.indexOf(screen)
        if (existing >= 0) {
            // Go back to the screen already in the stack (e.g. Player → Guide → Player).
            while (backStack.lastIndex > existing) backStack.removeAt(backStack.lastIndex)
        } else {
            backStack.add(screen)
        }
    }

    /** Returns false when there is nothing left to go back to. */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    // ---- Playlist & EPG ---------------------------------------------------------------------

    private fun load(source: PlaylistSource, refresh: Boolean, forceEpg: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update { it.copy(playlistLoading = true, playlistError = null, epgError = null) }
            var playlist: Playlist? = null
            if (repo.hasCachedPlaylist(source)) {
                playlist = runCatching { repo.loadPlaylist(source, refresh = false) }.getOrNull()
                playlist?.let { applyPlaylist(it) }
            }
            if (refresh || playlist == null) {
                try {
                    val fresh = repo.loadPlaylist(source, refresh = true)
                    playlist = fresh
                    applyPlaylist(fresh)
                } catch (e: Exception) {
                    if (playlist == null) {
                        _ui.update { it.copy(playlistError = e.message ?: e.javaClass.simpleName) }
                    }
                }
            }
            _ui.update { it.copy(playlistLoading = false) }
            val loaded = playlist ?: return@launch
            if (repo.epgUrls(source, loaded).isEmpty()) {
                _ui.update { it.copy(epg = EpgData.EMPTY) }
                return@launch
            }

            _ui.update { it.copy(epgLoading = true) }
            val age = repo.epgAge(source)
            if (age != null) {
                runCatching { repo.loadEpg(source, loaded, refresh = false) }
                    .onSuccess { epg -> _ui.update { it.copy(epg = epg) } }
            }
            if (forceEpg || age == null || age > EPG_MAX_AGE) {
                try {
                    val epg = repo.loadEpg(source, loaded, refresh = true)
                    _ui.update { it.copy(epg = epg) }
                } catch (e: Exception) {
                    if (_ui.value.epg.isEmpty) _ui.update { it.copy(epgError = e.message ?: e.javaClass.simpleName) }
                }
            }
            _ui.update { it.copy(epgLoading = false) }
        }
    }

    private fun applyPlaylist(playlist: Playlist) {
        val groups = playlist.channels.mapNotNull { it.group }.distinct()
        _ui.update { it.copy(channels = playlist.channels, groups = groups, playlistError = null) }
        val s = settings.value ?: return
        if (!autoStartDone) {
            autoStartDone = true
            if (s.startOnLastChannel && backStack.lastOrNull() == Screen.Home) {
                playlist.channels.firstOrNull { it.key == s.lastChannelKey }?.let { playChannel(it) }
            }
        }
    }

    fun reloadActive(forceEpg: Boolean = true) {
        settings.value?.activePlaylist?.let { load(it, refresh = true, forceEpg = forceEpg) }
    }

    fun setActivePlaylist(source: PlaylistSource) {
        viewModelScope.launch {
            settingsRepo.setActivePlaylist(source.id)
            _ui.value = UiState()
            load(source, refresh = true)
        }
    }

    fun savePlaylist(source: PlaylistSource) {
        viewModelScope.launch {
            val wasEmpty = settings.value?.playlists.isNullOrEmpty()
            val isActive = settings.value?.activePlaylist?.id == source.id
            settingsRepo.savePlaylist(source)
            back()
            if (wasEmpty) {
                backStack.clear()
                backStack.add(Screen.Home)
            }
            val nowActive = settings.filterNotNull().first { s -> s.playlists.any { it.id == source.id } }.activePlaylist
            if (wasEmpty || isActive || nowActive?.id == source.id) {
                _ui.value = UiState()
                load(source, refresh = true, forceEpg = true)
            }
        }
    }

    fun deletePlaylist(source: PlaylistSource) {
        viewModelScope.launch {
            val wasActive = settings.value?.activePlaylist?.id == source.id
            settingsRepo.deletePlaylist(source.id)
            repo.deleteCache(source)
            val s = settings.filterNotNull().first { st -> st.playlists.none { it.id == source.id } }
            if (s.playlists.isEmpty()) {
                loadJob?.cancel()
                _ui.value = UiState()
                backStack.clear()
                backStack.add(Screen.Welcome)
            } else if (wasActive) {
                _ui.value = UiState()
                s.activePlaylist?.let { load(it, refresh = true) }
            }
        }
    }

    // ---- Playback ---------------------------------------------------------------------------

    /** Plays [channel] full screen. If it is already playing (e.g. as a preview) it is not restarted. */
    fun playChannel(channel: Channel) {
        val current = _ui.value.currentKey
        if (current != channel.key) previousKey = current
        _ui.update { it.copy(currentKey = channel.key) }
        if (player.playingKey.value != channel.key) player.play(channel)
        viewModelScope.launch { settingsRepo.setLastChannel(channel.key) }
        navigate(Screen.Player)
    }

    /** Plays [channel] in a small preview without leaving the current screen. */
    fun preview(channel: Channel) {
        if (player.playingKey.value == channel.key) return
        _ui.update { it.copy(currentKey = channel.key) }
        player.play(channel)
    }

    fun stopPlayback() {
        if (player.playingKey.value != null) player.stop()
    }

    /** Moves by [delta] positions in the LCN-sorted channel list, wrapping around. */
    fun zap(delta: Int) {
        val channels = _ui.value.channels
        if (channels.isEmpty()) return
        val index = channels.indexOfFirst { it.key == _ui.value.currentKey }.coerceAtLeast(0)
        val next = Math.floorMod(index + delta, channels.size)
        playChannel(channels[next])
    }

    fun playNumber(number: Int): Boolean {
        val channels = _ui.value.channels
        val target = channels.firstOrNull { it.number == number }
            ?: channels.filter { it.number > number }.minByOrNull { it.number }
            ?: return false
        playChannel(target)
        return true
    }

    fun playPrevious() {
        val key = previousKey ?: return
        _ui.value.channels.firstOrNull { it.key == key }?.let { playChannel(it) }
    }

    fun currentChannel(): Channel? = _ui.value.let { s -> s.channels.firstOrNull { it.key == s.currentKey } }

    // ---- Settings ---------------------------------------------------------------------------

    fun toggleFavorite(channel: Channel) = viewModelScope.launch { settingsRepo.toggleFavorite(channel.key) }
    fun setInvertUpDown(value: Boolean) = viewModelScope.launch { settingsRepo.setInvertUpDown(value) }
    fun setStartOnLastChannel(value: Boolean) = viewModelScope.launch { settingsRepo.setStartOnLastChannel(value) }
    fun setAutoUpdateCheck(value: Boolean) = viewModelScope.launch { settingsRepo.setAutoUpdateCheck(value) }
    fun setThemeColor(value: String) = viewModelScope.launch { settingsRepo.setThemeColor(value) }
    fun setThemeMode(value: String) = viewModelScope.launch { settingsRepo.setThemeMode(value) }

    fun setPreferredAudio(value: String) = viewModelScope.launch {
        settingsRepo.setPreferredAudio(value)
        player.preferredAudio = value
    }

    // ---- Updates ----------------------------------------------------------------------------

    fun checkForUpdates() = viewModelScope.launch {
        settingsRepo.setLastUpdateCheck(System.currentTimeMillis())
        if (updateManager.check() is UpdateStatus.Available) _showUpdateDialog.value = true
    }

    fun installUpdate(info: UpdateInfo) = viewModelScope.launch {
        _showUpdateDialog.value = true
        updateManager.downloadAndInstall(info)
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
        updateManager.dismiss()
    }

    override fun onCleared() {
        player.release()
    }

    private companion object {
        const val EPG_MAX_AGE = 12L * 60 * 60 * 1000
        const val UPDATE_CHECK_INTERVAL = 6L * 60 * 60 * 1000
    }
}
