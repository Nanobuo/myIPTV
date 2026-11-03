package io.github.nanobuo.myiptv.util

import android.content.Context
import io.github.nanobuo.myiptv.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom

/** Data sent from the phone form. */
data class RemotePlaylist(val name: String, val url: String, val epgUrl: String, val userAgent: String)

/**
 * Tiny web server used to type the playlist address on a phone instead of with the remote.
 * It runs only while the QR screen is open and answers only on a random secret path.
 */
class RemoteInputServer(
    private val context: Context,
    private val onReceived: (RemotePlaylist) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ServerSocket? = null
    /** The form is accepted once: a second submission must not add the playlist again. */
    private val received = java.util.concurrent.atomic.AtomicBoolean(false)
    private val token = buildString {
        val random = SecureRandom()
        val chars = "abcdefghijkmnpqrstuvwxyz23456789"
        repeat(8) { append(chars[random.nextInt(chars.length)]) }
    }

    /** Address to open on the phone, or null when the TV is not on a local network. */
    var address: String? = null
        private set

    fun start(): String? {
        val ip = localIpAddress() ?: return null
        val socket = PORTS.firstNotNullOfOrNull { port -> runCatching { ServerSocket(port) }.getOrNull() }
            ?: runCatching { ServerSocket(0) }.getOrNull()
            ?: return null
        server = socket
        address = "http://$ip:${socket.localPort}/$token"
        scope.launch {
            while (isActive) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch { runCatching { client.use { handle(it) } } }
            }
        }
        return address
    }

    fun stop() {
        runCatching { server?.close() }
        scope.cancel()
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 15_000
        val input = BufferedInputStream(socket.getInputStream())
        val requestLine = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val parts = requestLine.split(' ')
        val method = parts.getOrNull(0).orEmpty()
        val path = parts.getOrNull(1).orEmpty().substringBefore('?').trimEnd('/')

        val out = socket.getOutputStream()
        if (path != "/$token") {
            respond(out, 404, page(context.getString(R.string.remote_page_not_found)))
            return
        }
        if (method == "POST") {
            val length = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 64 * 1024) ?: 0
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(body, read, length - read)
                if (n < 0) break
                read += n
            }
            val form = parseForm(String(body, 0, read, Charsets.UTF_8))
            val url = form["url"].orEmpty().trim()
            if (url.isEmpty()) {
                respond(out, 200, formPage(error = context.getString(R.string.error_missing_source)))
                return
            }
            if (!received.compareAndSet(false, true)) {
                respond(out, 200, page("✅ " + context.getString(R.string.remote_page_done)))
                return
            }
            onReceived(
                RemotePlaylist(
                    name = form["name"].orEmpty().trim(),
                    url = url,
                    epgUrl = form["epg"].orEmpty().trim(),
                    userAgent = form["ua"].orEmpty().trim(),
                ),
            )
            respond(out, 200, page("✅ " + context.getString(R.string.remote_page_done)))
        } else {
            respond(out, 200, formPage(error = null))
        }
    }

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buffer.size() == 0) null else buffer.toString(Charsets.UTF_8.name())
            if (b == '\n'.code) return buffer.toString(Charsets.UTF_8.name()).trimEnd('\r')
            if (buffer.size() > 8 * 1024) return null
            buffer.write(b)
        }
    }

    private fun parseForm(body: String): Map<String, String> = body.split('&').mapNotNull { pair ->
        val eq = pair.indexOf('=')
        if (eq <= 0) return@mapNotNull null
        val key = URLDecoder.decode(pair.substring(0, eq), "UTF-8")
        val value = URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
        key to value
    }.toMap()

    private fun respond(out: java.io.OutputStream, code: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val status = if (code == 200) "200 OK" else "404 Not Found"
        out.write(
            ("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII),
        )
        out.write(bytes)
        out.flush()
    }

    private fun esc(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun formPage(error: String?): String {
        fun field(name: String, label: String, type: String, hint: String = "") =
            """<label>${esc(label)}<input name="$name" type="$type" autocomplete="off" autocapitalize="off" spellcheck="false"></label>""" +
                (if (hint.isNotEmpty()) """<small>${esc(hint)}</small>""" else "")
        val content = buildString {
            append("<h1>myIPTV</h1><p>${esc(context.getString(R.string.remote_page_intro))}</p>")
            if (error != null) append("""<p class="err">${esc(error)}</p>""")
            append("""<form method="post">""")
            append(field("name", context.getString(R.string.playlist_name), "text"))
            append(field("url", context.getString(R.string.playlist_url), "url"))
            append(field("epg", context.getString(R.string.epg_url), "url", context.getString(R.string.epg_url_hint)))
            append(field("ua", context.getString(R.string.user_agent), "text", context.getString(R.string.user_agent_hint)))
            append("""<button type="submit">${esc(context.getString(R.string.remote_page_send))}</button></form>""")
        }
        return page(content, raw = true)
    }

    private fun page(content: String, raw: Boolean = false): String = """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>myIPTV</title><style>
:root{color-scheme:light dark;--bg:#f2f2f7;--card:#fff;--text:#1d1d1f;--muted:#6e6e73;--accent:#4f7df3}
@media (prefers-color-scheme:dark){:root{--bg:#1c1c1e;--card:#2c2c2e;--text:#f2f2f7;--muted:#aeaeb2}}
body{margin:0;padding:16px;background:var(--bg);color:var(--text);font:16px/1.4 system-ui,-apple-system,sans-serif}
main{max-width:520px;margin:0 auto;background:var(--card);border-radius:20px;padding:20px}
h1{margin:0 0 8px;font-size:24px}p{color:var(--muted)}label{display:block;margin-top:14px;font-weight:600}
input{display:block;box-sizing:border-box;width:100%;margin-top:6px;padding:12px;border-radius:12px;border:1px solid var(--muted);
background:transparent;color:var(--text);font-size:16px}small{color:var(--muted)}
button{margin-top:20px;width:100%;padding:14px;border:0;border-radius:24px;background:var(--accent);color:#fff;font-size:17px;font-weight:600}
.err{color:#ff453a}</style></head><body><main>${if (raw) content else "<h1>myIPTV</h1><p>${esc(content)}</p>"}</main></body></html>"""

    companion object {
        private val PORTS = listOf(8765, 8766, 8767)

        /** IPv4 address of the TV on the local network (Wi-Fi or Ethernet). */
        fun localIpAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { if (it.name.startsWith("eth") || it.name.startsWith("wlan")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
