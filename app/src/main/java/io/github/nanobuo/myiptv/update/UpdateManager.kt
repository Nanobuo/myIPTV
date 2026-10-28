package io.github.nanobuo.myiptv.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.github.nanobuo.myiptv.BuildConfig
import io.github.nanobuo.myiptv.data.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val size: Long,
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val info: UpdateInfo) : UpdateStatus
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateStatus
    data object Installing : UpdateStatus
    data class NeedsPermission(val info: UpdateInfo) : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

/** Checks GitHub Releases for a newer APK and installs it with [PackageInstaller]. */
class UpdateManager(
    private val context: Context,
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME.substringBefore('-')

    fun dismiss() {
        _status.value = UpdateStatus.Idle
    }

    internal fun reportInstallResult(success: Boolean, message: String?) {
        _status.value = if (success) UpdateStatus.Idle else UpdateStatus.Failed(message ?: "")
    }

    suspend fun check(): UpdateStatus {
        _status.value = UpdateStatus.Checking
        val result = try {
            val info = fetchLatest()
            if (info != null && isNewer(info.version, currentVersion)) UpdateStatus.Available(info) else UpdateStatus.UpToDate
        } catch (e: Exception) {
            UpdateStatus.Failed(e.message ?: e.javaClass.simpleName)
        }
        _status.value = result
        return result
    }

    private suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", Network.defaultUserAgent)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val release = json.decodeFromString(Release.serializer(), response.body.string())
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return@withContext null
            UpdateInfo(
                version = release.tagName.removePrefix("v").removePrefix("V"),
                notes = plainText(release.body.orEmpty()),
                apkUrl = apk.downloadUrl,
                size = apk.size,
            )
        }
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user allows this app to install updates. */
    fun openInstallPermissionSettings(): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: Exception) {
        false
    }

    suspend fun downloadAndInstall(info: UpdateInfo) {
        if (!canInstall()) {
            _status.value = UpdateStatus.NeedsPermission(info)
            return
        }
        try {
            _status.value = UpdateStatus.Downloading(info, 0f)
            val file = File(File(context.cacheDir, "updates"), "myIPTV-${info.version}.apk")
            withContext(Dispatchers.IO) {
                Network.download(client, info.apkUrl, file, null) { done, total ->
                    val size = if (total > 0) total else info.size
                    if (size > 0) _status.value = UpdateStatus.Downloading(info, done.toFloat() / size)
                }
            }
            _status.value = UpdateStatus.Installing
            withContext(Dispatchers.IO) { install(file) }
        } catch (e: Exception) {
            _status.value = UpdateStatus.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("myIPTV.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    companion object {
        /** Release notes are Markdown: show them as plain text in the update dialog. */
        fun plainText(markdown: String): String = markdown.lines().joinToString("\n") { line ->
            line.trimEnd()
                .replace(Regex("^#{1,6}\\s*"), "")
                .replace(Regex("^\\s*[-*]\\s+"), "• ")
                .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                .replace(Regex("\\[(.+?)]\\((.+?)\\)"), "$1")
                .replace("`", "")
        }.replace(Regex("\n{3,}"), "\n\n").trim()

        /** Compares dotted versions such as 1.10.0 and 1.9.2. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }

    @Serializable
    private data class Release(
        @kotlinx.serialization.SerialName("tag_name") val tagName: String,
        val body: String? = null,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String,
        @kotlinx.serialization.SerialName("browser_download_url") val downloadUrl: String,
        val size: Long = 0,
    )
}
