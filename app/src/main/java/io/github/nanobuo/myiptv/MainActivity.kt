package io.github.nanobuo.myiptv

import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateOf
import io.github.nanobuo.myiptv.ui.mobile.MobileRoot
import io.github.nanobuo.myiptv.ui.mobile.findActivity
import io.github.nanobuo.myiptv.util.DisplayModes
import io.github.nanobuo.myiptv.util.TvHome
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.nanobuo.myiptv.ui.screens.GuideScreen
import io.github.nanobuo.myiptv.ui.screens.RemoteAddScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import io.github.nanobuo.myiptv.data.AppSettings
import io.github.nanobuo.myiptv.ui.MainViewModel
import io.github.nanobuo.myiptv.ui.Screen
import io.github.nanobuo.myiptv.ui.screens.HomeScreen
import io.github.nanobuo.myiptv.ui.screens.PlayerScreen
import io.github.nanobuo.myiptv.ui.screens.PlaylistEditorScreen
import io.github.nanobuo.myiptv.ui.screens.SettingsScreen
import io.github.nanobuo.myiptv.ui.screens.CrashDialogHost
import io.github.nanobuo.myiptv.ui.screens.UpdateDialogHost
import io.github.nanobuo.myiptv.ui.screens.WelcomeScreen
import io.github.nanobuo.myiptv.ui.theme.MyIptvTheme
import io.github.nanobuo.myiptv.ui.theme.ThemeColor
import io.github.nanobuo.myiptv.ui.theme.supportsDynamicColor
import io.github.nanobuo.myiptv.util.LocaleHelper

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    /** Google TV / Android TV get the remote-control interface, everything else the touch one. */
    private val isTv by lazy { isTelevision() }

    private val pipActive = mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isTv) enableEdgeToEdge()
        handleLink(intent)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val theme = settings?.themeColor ?: AppSettings.THEME_DYNAMIC
            val light = when (settings?.themeMode) {
                AppSettings.MODE_LIGHT -> true
                AppSettings.MODE_SYSTEM -> !isSystemInDarkTheme()
                else -> false
            }
            MyIptvTheme(
                themeColor = if (theme == AppSettings.THEME_DYNAMIC && !supportsDynamicColor) ThemeColor.BLUE.key else theme,
                light = light,
            ) {
                val background = MaterialTheme.colorScheme.background
                SideEffect { window.decorView.setBackgroundColor(background.toArgb()) }
                if (isTv) {
                    AppRoot(vm, onExit = { finish() })
                } else {
                    MobileRoot(
                        vm = vm,
                        inPip = pipActive.value,
                        onEnterPip = ::enterPip,
                        onPipStateChanged = ::updatePipParams,
                        onExit = { finish() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    /** myiptv://channel/<key>, used by the TV home screen rows. */
    private fun handleLink(intent: Intent?) {
        TvHome.keyFromLink(intent?.data)?.let(vm::openChannel)
    }

    // ---- Picture-in-Picture (phones and tablets) ----------------------------------------------

    private val pipSupported: Boolean
        get() = !isTv && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** Automatic PiP when leaving the app while a channel plays full screen. */
    private fun shouldAutoPip(): Boolean =
        pipSupported &&
            vm.settings.value?.pipEnabled != false &&
            vm.backStack.lastOrNull() == Screen.Player &&
            vm.player.playingKey.value != null

    private fun pipParams(): PictureInPictureParams {
        val aspect = vm.player.state.value.videoAspect
        val ratio = if (aspect in 0.5f..2.3f) Rational((aspect * 1000).toInt(), 1000) else Rational(16, 9)
        return PictureInPictureParams.Builder()
            .setAspectRatio(ratio)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(shouldAutoPip())
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
    }

    private fun updatePipParams() {
        if (pipSupported) runCatching { setPictureInPictureParams(pipParams()) }
    }

    private fun enterPip() {
        if (pipSupported) runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters PiP by itself (setAutoEnterEnabled).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && shouldAutoPip()) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipActive.value = isInPictureInPictureMode
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) {
            // The small window was closed (not expanded): stop playing.
            vm.stopPlayback()
        }
    }
}

private fun Context.isTelevision(): Boolean =
    getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

@Composable
private fun AppRoot(vm: MainViewModel, onExit: () -> Unit) {
    // Registered first, so screen-specific handlers take precedence.
    BackHandler {
        if (!vm.back()) onExit()
    }
    val toastContext = LocalContext.current
    LaunchedEffect(vm) {
        vm.messages.collect { android.widget.Toast.makeText(toastContext, it, android.widget.Toast.LENGTH_LONG).show() }
    }
    // The player belongs to the whole app: it keeps playing while moving between the preview,
    // the guide and full screen, and is released when the app goes to background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.player.ensurePlayer()
                Lifecycle.Event.ON_STOP -> vm.player.release()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val playingKey by vm.player.playingKey.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(view, playingKey != null) {
        view.keepScreenOn = playingKey != null
        onDispose { view.keepScreenOn = false }
    }
    val screen = vm.backStack.lastOrNull()
    LaunchedEffect(screen) {
        // Screens without video stop playback.
        if (screen == Screen.Settings || screen == Screen.Welcome || screen is Screen.PlaylistEditor || screen == Screen.RemoteAdd) {
            vm.stopPlayback()
        }
    }

    // Match the display refresh rate to the channel while watching full screen (e.g. 50 Hz).
    val tvSettings by vm.settings.collectAsStateWithLifecycle()
    val frameRate by vm.player.frameRate.collectAsStateWithLifecycle()
    val matchFrameRate = tvSettings?.matchFrameRate != false
    LaunchedEffect(frameRate, matchFrameRate, screen == Screen.Player, playingKey) {
        val activity = toastContext.findActivity() ?: return@LaunchedEffect
        when {
            !matchFrameRate || playingKey == null -> DisplayModes.apply(activity, null)
            screen == Screen.Player && frameRate != null -> DisplayModes.apply(activity, frameRate)
        }
    }

    // TV home screen: favorites row and "Continue watching".
    val homeUi by vm.ui.collectAsStateWithLifecycle()
    val appContext = toastContext.applicationContext
    LaunchedEffect(homeUi.channels, tvSettings?.favorites) {
        if (homeUi.channels.isNotEmpty()) {
            withContext(Dispatchers.IO) { TvHome.syncChannelRow(appContext, homeUi.channels, tvSettings?.favorites.orEmpty()) }
        }
    }
    LaunchedEffect(playingKey, screen == Screen.Player) {
        val channel = homeUi.channels.firstOrNull { it.key == playingKey }
        if (screen == Screen.Player && channel != null) {
            val programme = homeUi.epg.nowAndNext(channel.key).first?.title
            withContext(Dispatchers.IO) { TvHome.updateWatchNext(appContext, channel, programme) }
        }
    }

    val stateHolder = rememberSaveableStateHolder()
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (screen) {
            null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            else -> stateHolder.SaveableStateProvider(screen.toString()) {
                when (screen) {
                    Screen.Welcome -> WelcomeScreen(vm)
                    Screen.Home -> HomeScreen(vm)
                    Screen.Player -> PlayerScreen(vm)
                    Screen.Settings -> SettingsScreen(vm)
                    Screen.Guide -> GuideScreen(vm)
                    Screen.RemoteAdd -> RemoteAddScreen(vm)
                    is Screen.PlaylistEditor -> PlaylistEditorScreen(vm, screen.playlistId)
                }
            }
        }
        UpdateDialogHost(vm)
        CrashDialogHost(vm)
    }
}
