package io.github.nanobuo.myiptv

import android.app.Application
import android.content.Context
import io.github.nanobuo.myiptv.data.Network
import io.github.nanobuo.myiptv.data.PlaylistRepository
import io.github.nanobuo.myiptv.data.SettingsRepository
import io.github.nanobuo.myiptv.player.PlayerController
import io.github.nanobuo.myiptv.update.UpdateManager
import io.github.nanobuo.myiptv.util.LocaleHelper

class MyIptvApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Simple manual dependency container. */
class AppContainer(context: Context) {
    val httpClient = Network.createClient(context)
    val settings = SettingsRepository(context)
    val playlists = PlaylistRepository(context, httpClient)
    // Streams must never be served from the HTTP cache.
    val player = PlayerController(context, httpClient.newBuilder().cache(null).build())
    val updateManager = UpdateManager(context, httpClient)
}
