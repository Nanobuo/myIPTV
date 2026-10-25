package io.github.nanobuo.myiptv.data

import android.content.Context
import io.github.nanobuo.myiptv.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

object Network {
    val defaultUserAgent = "myIPTV/${BuildConfig.VERSION_NAME} (Linux; Android TV)"

    fun createClient(context: Context): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cache(okhttp3.Cache(File(context.cacheDir, "http"), 20L * 1024 * 1024))
        .build()

    /** Downloads [url] into [target] atomically. */
    @Throws(IOException::class)
    fun download(client: OkHttpClient, url: String, target: File, userAgent: String?, onProgress: ((Long, Long) -> Unit)? = null) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent?.takeIf { it.isNotBlank() } ?: defaultUserAgent)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            val total = body.contentLength()
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".tmp")
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        done += read
                        onProgress?.invoke(done, total)
                    }
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    /** Wraps the stream with GZIP decompression when the content is gzipped. */
    fun maybeGunzip(input: InputStream): InputStream {
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input, 64 * 1024)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        return if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(buffered, 64 * 1024), 64 * 1024) else buffered
    }
}
