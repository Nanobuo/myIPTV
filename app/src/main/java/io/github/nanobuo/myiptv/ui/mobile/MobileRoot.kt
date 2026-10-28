package io.github.nanobuo.myiptv.ui.mobile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nanobuo.myiptv.R
import io.github.nanobuo.myiptv.playback.PlaybackService
import io.github.nanobuo.myiptv.ui.MainViewModel
import io.github.nanobuo.myiptv.ui.Screen

/** Size class of the device, from the smallest side of the screen. */
@Composable
fun isTablet(): Boolean = LocalConfiguration.current.smallestScreenWidthDp >= 600

/** True when there is room for a list and a detail pane side by side. */
@Composable
fun isTwoPane(): Boolean = LocalConfiguration.current.screenWidthDp >= 700

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private enum class Tab(val screen: Screen, val label: Int, val icon: ImageVector) {
    Channels(Screen.Home, R.string.tab_channels, Icons.Default.LiveTv),
    Guide(Screen.Guide, R.string.tv_guide, Icons.Default.CalendarMonth),
    Settings(Screen.Settings, R.string.settings, Icons.Default.Settings),
}

/** Touch interface for phones and tablets. */
@Composable
fun MobileRoot(
    vm: MainViewModel,
    inPip: Boolean,
    onEnterPip: () -> Unit,
    onPipStateChanged: () -> Unit,
    onExit: () -> Unit,
) {
    BackHandler {
        if (!vm.back()) onExit()
    }
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val playingKey by vm.player.playingKey.collectAsStateWithLifecycle()
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val screen = vm.backStack.lastOrNull()
    val tablet = isTablet()
    val backgroundAudio = settings?.backgroundAudio != false

    LaunchedEffect(vm) {
        vm.messages.collect { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }
    }

    // ---- Player lifecycle ------------------------------------------------------------------
    // With background audio the player keeps going when the app is not visible (video off);
    // otherwise it is released and recreated on return, like on TV.
    val currentInPip by rememberUpdatedState(inPip)
    val currentBackgroundAudio by rememberUpdatedState(backgroundAudio)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    vm.player.ensurePlayer()
                    vm.player.setVideoEnabled(true)
                }
                Lifecycle.Event.ON_STOP -> when {
                    currentInPip || context.findActivity()?.isInPictureInPictureMode == true -> Unit
                    currentBackgroundAudio && vm.player.playingKey.value != null -> vm.player.setVideoEnabled(false)
                    else -> vm.player.release()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Background playback service (media notification, lock screen controls).
    var notificationAsked by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(playingKey != null, backgroundAudio) {
        val intent = Intent(context, PlaybackService::class.java)
        if (backgroundAudio && playingKey != null) {
            runCatching { context.startService(intent) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationAsked &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationAsked = true
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else if (!backgroundAudio) {
            runCatching { context.stopService(intent) }
        }
    }

    // ---- Screen-dependent behaviour ----------------------------------------------------------
    LaunchedEffect(screen, tablet) {
        when {
            // Screens without video stop playback; on phones the channel list has no preview.
            screen == Screen.Settings || screen == Screen.Welcome || screen is Screen.PlaylistEditor -> vm.stopPlayback()
            screen == Screen.Home && !tablet -> vm.stopPlayback()
        }
    }
    LaunchedEffect(screen, playingKey, settings?.pipEnabled, playerState.videoAspect) { onPipStateChanged() }

    val view = LocalView.current
    val activity = context.findActivity()
    val fullScreen = screen == Screen.Player
    DisposableEffect(fullScreen, tablet, activity) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (fullScreen) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        // Phones watch in landscape; tablets follow the device.
        activity?.requestedOrientation = if (fullScreen && !tablet) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { }
    }
    DisposableEffect(view, playingKey != null) {
        view.keepScreenOn = playingKey != null
        onDispose { view.keepScreenOn = false }
    }

    // ---- Content ---------------------------------------------------------------------------
    val stateHolder = rememberSaveableStateHolder()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (screen) {
            null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            Screen.Player -> MobilePlayerScreen(vm, inPip = inPip, onEnterPip = onEnterPip)
            Screen.Welcome -> MobileWelcomeScreen(vm)
            is Screen.PlaylistEditor -> MobilePlaylistEditor(vm, screen.playlistId)
            else -> {
                val tab = Tab.entries.firstOrNull { it.screen == screen } ?: Tab.Channels
                TabScaffold(selected = tab, tablet = tablet, onSelect = { vm.openTab(it.screen) }) { padding ->
                    stateHolder.SaveableStateProvider(tab.name) {
                        when (tab) {
                            Tab.Channels -> MobileChannelsScreen(vm, padding)
                            Tab.Guide -> MobileGuideScreen(vm, padding)
                            Tab.Settings -> MobileSettingsScreen(vm, padding)
                        }
                    }
                }
            }
        }
        if (!inPip) MobileUpdateDialog(vm)
    }
}

@Composable
private fun TabScaffold(
    selected: Tab,
    tablet: Boolean,
    onSelect: (Tab) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (tablet) {
        // Tablets: navigation rail on the side.
        Row(Modifier.fillMaxSize()) {
            NavigationRail(Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
                Tab.entries.forEach { tab ->
                    NavigationRailItem(
                        selected = tab == selected,
                        onClick = { onSelect(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
            Scaffold(modifier = Modifier.weight(1f)) { padding -> content(padding) }
        }
    } else {
        // Phones: navigation bar at the bottom.
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == selected,
                            onClick = { onSelect(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            },
        ) { padding -> content(padding) }
    }
}
