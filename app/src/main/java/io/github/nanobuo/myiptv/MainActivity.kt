package io.github.nanobuo.myiptv

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.nanobuo.myiptv.ui.screens.GuideScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import io.github.nanobuo.myiptv.data.AppSettings
import io.github.nanobuo.myiptv.ui.MainViewModel
import io.github.nanobuo.myiptv.ui.Screen
import io.github.nanobuo.myiptv.ui.screens.HomeScreen
import io.github.nanobuo.myiptv.ui.screens.PlayerScreen
import io.github.nanobuo.myiptv.ui.screens.PlaylistEditorScreen
import io.github.nanobuo.myiptv.ui.screens.SettingsScreen
import io.github.nanobuo.myiptv.ui.screens.UpdateDialogHost
import io.github.nanobuo.myiptv.ui.screens.WelcomeScreen
import io.github.nanobuo.myiptv.ui.theme.MyIptvTheme
import io.github.nanobuo.myiptv.ui.theme.ThemeColor
import io.github.nanobuo.myiptv.ui.theme.supportsDynamicColor
import io.github.nanobuo.myiptv.util.LocaleHelper

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val theme = settings?.themeColor ?: AppSettings.THEME_DYNAMIC
            val light = settings?.themeMode == AppSettings.MODE_LIGHT
            MyIptvTheme(
                themeColor = if (theme == AppSettings.THEME_DYNAMIC && !supportsDynamicColor) ThemeColor.BLUE.key else theme,
                light = light,
            ) {
                val background = MaterialTheme.colorScheme.background
                SideEffect { window.decorView.setBackgroundColor(background.toArgb()) }
                AppRoot(vm, onExit = { finish() })
            }
        }
    }
}

@Composable
private fun AppRoot(vm: MainViewModel, onExit: () -> Unit) {
    // Registered first, so screen-specific handlers take precedence.
    BackHandler {
        if (!vm.back()) onExit()
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
        if (screen == Screen.Settings || screen == Screen.Welcome || screen is Screen.PlaylistEditor) vm.stopPlayback()
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
                    is Screen.PlaylistEditor -> PlaylistEditorScreen(vm, screen.playlistId)
                }
            }
        }
        UpdateDialogHost(vm)
    }
}
