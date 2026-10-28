package io.github.nanobuo.myiptv.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Loads playlists and EPG, keeping a local copy so the app opens quickly and works offline. */
class PlaylistRepository(
    private val context: Context,
    private val client: OkHttpClient,
) {
    private val playlistDir = File(context.filesDir, "playlists")
    private val epgDir = File(context.filesDir, "epg")

    private fun playlistCache(source: PlaylistSource) = File(playlistDir, "${source.id}.m3u")
    private fun epgCache(source: PlaylistSource, index: Int) = File(epgDir, "${source.id}_$index.xml")

    fun hasCachedPlaylist(source: PlaylistSource) = playlistCache(source).exists()

    /** Age of the cached EPG in milliseconds, or null when missing. */
    fun epgAge(source: PlaylistSource): Long? =
        epgCache(source, 0).takeIf { it.exists() }?.let { System.currentTimeMillis() - it.lastModified() }

    /**
     * Returns the playlist. With [refresh] the source is read again (downloaded for URLs) and
     * a failure is reported to the caller; otherwise the local copy is used when available.
     */
    suspend fun loadPlaylist(source: PlaylistSource, refresh: Boolean): Playlist = withContext(Dispatchers.IO) {
        val cache = playlistCache(source)
        if (refresh || !cache.exists()) fetchPlaylist(source, cache)
        cache.inputStream().use { input ->
            Network.maybeGunzip(input).bufferedReader().use { M3uParser.parse(it, source.userAgent) }
        }
    }

    private fun fetchPlaylist(source: PlaylistSource, cache: File) {
        when (source.type) {
            SourceType.URL -> Network.download(client, source.location, cache, source.userAgent)
            SourceType.CONTENT_URI -> copyToCache(openContentUri(source.location), cache)
            SourceType.FILE_PATH -> copyToCache(File(source.location).inputStream(), cache)
        }
    }

    private fun openContentUri(uri: String): InputStream =
        context.contentResolver.openInputStream(Uri.parse(uri)) ?: throw IOException("Cannot open $uri")

    private fun copyToCache(input: InputStream, cache: File) {
        cache.parentFile?.mkdirs()
        val tmp = File(cache.parentFile, cache.name + ".tmp")
        input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        if (!tmp.renameTo(cache)) {
            tmp.copyTo(cache, overwrite = true)
            tmp.delete()
        }
    }

    /** EPG addresses for a playlist: the one set by the user first, then those declared in the file. */
    fun epgUrls(source: PlaylistSource, playlist: Playlist): List<String> =
        (listOfNotNull(source.epgUrl?.trim()?.takeIf { it.isNotEmpty() }) + playlist.epgUrls).distinct()

    /** Loads the EPG for [playlist], downloading it again when [refresh] is set or no copy exists. */
    suspend fun loadEpg(source: PlaylistSource, playlist: Playlist, refresh: Boolean): EpgData = withContext(Dispatchers.IO) {
        val urls = epgUrls(source, playlist)
        val userAgent = source.userAgent ?: playlist.channels.firstNotNullOfOrNull { it.headers[M3uParser.HEADER_USER_AGENT] }
        val merged = HashMap<String, MutableList<Programme>>()
        var lastError: Exception? = null
        urls.forEachIndexed { index, url ->
            val cache = epgCache(source, index)
            try {
                if (refresh || !cache.exists()) {
                    try {
                        if (url.startsWith("content://")) {
                            copyToCache(openContentUri(url), cache)
                        } else if (url.startsWith("/")) {
                            copyToCache(File(url).inputStream(), cache)
                        } else {
                            Network.download(client, url, cache, userAgent)
                        }
                    } catch (e: Exception) {
                        if (!cache.exists()) throw e
                    }
                }
                cache.inputStream().use { input ->
                    XmltvParser.parse(Network.maybeGunzip(input), playlist.channels).forEach { (key, list) ->
                        // The first EPG source providing a channel wins.
                        merged.putIfAbsent(key, list)
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        if (merged.isEmpty() && lastError != null) throw lastError!!
        EpgData(merged)
    }

    fun deleteCache(source: PlaylistSource) {
        playlistCache(source).delete()
        epgDir.listFiles()?.filter { it.name.startsWith(source.id + "_") }?.forEach { it.delete() }
    }
}
