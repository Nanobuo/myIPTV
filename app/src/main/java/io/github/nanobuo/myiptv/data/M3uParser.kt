package io.github.nanobuo.myiptv.data

import java.io.BufferedReader
import java.net.URLDecoder

/**
 * Parser for extended M3U playlists, including the most common IPTV extensions:
 * `#EXTINF` attributes, `#EXTGRP`, `#EXTVLCOPT`, `#EXTHTTP`, `#KODIPROP` and `url|Header=value`.
 */
object M3uParser {

    private val attrRegex = Regex("""([A-Za-z0-9_\-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,"']+))""")

    private val lcnAttributes = listOf("tvg-chno", "channel-number", "tvg-channel-number", "ch-number", "tvg-num", "channel-id-number")
    private val userAgentAttributes = listOf("user-agent", "http-user-agent", "tvg-user-agent")
    private val referrerAttributes = listOf("http-referrer", "http-referer", "referrer", "referer")

    fun parse(reader: BufferedReader, fallbackUserAgent: String? = null): Playlist {
        val epgUrls = mutableListOf<String>()
        var headerUserAgent: String? = fallbackUserAgent
        val raw = mutableListOf<RawEntry>()

        var current: RawEntry? = null
        var pendingGroup: String? = null
        // Options that may precede #EXTINF in some playlists.
        var pendingOptions = RawEntry()

        var first = true
        reader.lineSequence().forEach { rawLine ->
            var line = rawLine.trim()
            if (first) {
                line = line.removePrefix("﻿")
                first = false
            }
            if (line.isEmpty()) return@forEach

            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val attrs = parseAttributes(line.substringAfter("#EXTM3U", ""))
                    listOf("url-tvg", "x-tvg-url", "tvg-url").forEach { key ->
                        attrs[key]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { epgUrls += it }
                    }
                    userAgentAttributes.firstNotNullOfOrNull { attrs[it] }?.takeIf { it.isNotBlank() }?.let { headerUserAgent = it }
                }

                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val entry = pendingOptions
                    pendingOptions = RawEntry()
                    val body = line.substringAfter(':', "")
                    val commaIndex = findTitleComma(body)
                    val attrPart = if (commaIndex >= 0) body.substring(0, commaIndex) else body
                    val title = if (commaIndex >= 0) body.substring(commaIndex + 1).trim() else ""
                    val attrs = parseAttributes(attrPart)
                    entry.title = title
                    entry.tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
                    entry.tvgName = attrs["tvg-name"]?.takeIf { it.isNotBlank() }
                    entry.logo = (attrs["tvg-logo"] ?: attrs["logo"])?.takeIf { it.isNotBlank() }
                    entry.group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: pendingGroup
                    entry.lcn = lcnAttributes.firstNotNullOfOrNull { attrs[it]?.let(::parseLcn) }
                    userAgentAttributes.firstNotNullOfOrNull { attrs[it] }?.takeIf { it.isNotBlank() }
                        ?.let { entry.headers.putIfAbsent(HEADER_USER_AGENT, it) }
                    referrerAttributes.firstNotNullOfOrNull { attrs[it] }?.takeIf { it.isNotBlank() }
                        ?.let { entry.headers.putIfAbsent(HEADER_REFERER, it) }
                    current = entry
                }

                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    val group = line.substringAfter(':').trim().takeIf { it.isNotEmpty() }
                    val c = current
                    if (c != null) {
                        if (c.group == null) c.group = group
                    } else {
                        pendingGroup = group
                    }
                }

                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val target = current ?: pendingOptions
                    val option = line.substringAfter(':')
                    val key = option.substringBefore('=').trim().lowercase()
                    val value = option.substringAfter('=', "").trim()
                    if (value.isNotEmpty()) {
                        when (key) {
                            "http-user-agent" -> target.headers[HEADER_USER_AGENT] = value
                            "http-referrer", "http-referer" -> target.headers[HEADER_REFERER] = value
                            "http-origin" -> target.headers["Origin"] = value
                            "http-cookie" -> target.headers["Cookie"] = value
                        }
                    }
                }

                line.startsWith("#EXTHTTP:", ignoreCase = true) -> {
                    val target = current ?: pendingOptions
                    parseJsonHeaders(line.substringAfter(':')).forEach { (k, v) -> target.headers[normalizeHeader(k)] = v }
                }

                line.startsWith("#KODIPROP:", ignoreCase = true) -> {
                    val target = current ?: pendingOptions
                    val prop = line.substringAfter(':')
                    val key = prop.substringBefore('=').trim().lowercase()
                    val value = prop.substringAfter('=', "").trim()
                    when (key) {
                        "inputstream.adaptive.license_type" -> target.drmScheme = normalizeDrmScheme(value)
                        "inputstream.adaptive.license_key" -> target.drmLicense = value
                        "inputstream.adaptive.manifest_type" -> target.streamType = value.lowercase().let {
                            when (it) {
                                "mpd" -> "dash"
                                "ism" -> "ss"
                                else -> it
                            }
                        }
                        "inputstream.adaptive.stream_headers", "inputstream.adaptive.common_headers" ->
                            parseHeaderString(value).forEach { (k, v) -> target.headers[k] = v }
                    }
                }

                line.startsWith("#") -> Unit // Unknown directive or comment.

                else -> {
                    val entry = current ?: pendingOptions.also { pendingOptions = RawEntry() }
                    val pipe = line.indexOf('|')
                    if (pipe >= 0) {
                        entry.url = line.substring(0, pipe).trim()
                        parseHeaderString(line.substring(pipe + 1)).forEach { (k, v) -> entry.headers[k] = v }
                    } else {
                        entry.url = line
                    }
                    if (entry.title.isNullOrBlank()) entry.title = entry.tvgName ?: entry.url
                    raw += entry
                    current = null
                    pendingGroup = null
                }
            }
        }

        return Playlist(buildChannels(raw, headerUserAgent), epgUrls.distinct())
    }

    private fun buildChannels(raw: List<RawEntry>, defaultUserAgent: String?): List<Channel> {
        val withLcn = raw.filter { it.lcn != null }.sortedBy { it.lcn }
        val withoutLcn = raw.filter { it.lcn == null }
        var nextNumber = (withLcn.maxOfOrNull { it.lcn!! } ?: 0) + 1
        val usedKeys = HashMap<String, Int>()

        return (withLcn + withoutLcn).map { e ->
            val name = e.title?.takeIf { it.isNotBlank() } ?: e.tvgName ?: "?"
            val headers = LinkedHashMap(e.headers)
            if (defaultUserAgent != null && headers.keys.none { it.equals(HEADER_USER_AGENT, true) }) {
                headers[HEADER_USER_AGENT] = defaultUserAgent
            }
            val baseKey = "${e.tvgId.orEmpty().lowercase()}|${name.lowercase()}"
            val count = (usedKeys[baseKey] ?: 0) + 1
            usedKeys[baseKey] = count
            Channel(
                key = if (count == 1) baseKey else "$baseKey#$count",
                name = name,
                number = e.lcn ?: nextNumber++,
                hasLcn = e.lcn != null,
                tvgId = e.tvgId,
                tvgName = e.tvgName,
                logo = e.logo,
                group = e.group,
                url = e.url!!,
                headers = headers,
                drm = e.drmScheme?.let { DrmInfo(it, e.drmLicense) }
                    ?: e.drmLicense?.let { DrmInfo("clearkey", it) },
                streamType = e.streamType,
            )
        }
    }

    /** Finds the comma separating attributes from the title, ignoring commas inside quotes. */
    private fun findTitleComma(body: String): Int {
        var quote: Char? = null
        body.forEachIndexed { i, ch ->
            when {
                quote != null -> if (ch == quote) quote = null
                ch == '"' || ch == '\'' -> quote = ch
                ch == ',' -> return i
            }
        }
        return -1
    }

    internal fun parseAttributes(text: String): Map<String, String> =
        attrRegex.findAll(text).associate { m ->
            val v = m.groups[2]?.value ?: m.groups[3]?.value ?: m.groups[4]?.value ?: ""
            m.groupValues[1].lowercase() to v.trim()
        }

    private fun parseLcn(value: String): Int? =
        value.trim().toIntOrNull() ?: value.trim().toDoubleOrNull()?.toInt()

    /** Parses `User-Agent=foo&Referer=bar` (values may be URL-encoded). */
    internal fun parseHeaderString(text: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        text.split('&').forEach { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) return@forEach
            val key = part.substring(0, eq).trim()
            var value = part.substring(eq + 1).trim()
            if (value.contains('%')) {
                value = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
            }
            if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) value = value.substring(1, value.length - 1)
            if (key.isNotEmpty() && value.isNotEmpty()) result[normalizeHeader(key)] = value
        }
        return result
    }

    private fun parseJsonHeaders(json: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        Regex(""""([^"]+)"\s*:\s*"((?:[^"\\]|\\.)*)"""").findAll(json).forEach {
            result[it.groupValues[1]] = it.groupValues[2].replace("\\\"", "\"").replace("\\/", "/")
        }
        return result
    }

    private fun normalizeHeader(key: String): String = when (key.lowercase()) {
        "user-agent", "http-user-agent", "useragent" -> HEADER_USER_AGENT
        "referer", "referrer", "http-referrer", "http-referer" -> HEADER_REFERER
        "origin", "http-origin" -> "Origin"
        "cookie" -> "Cookie"
        else -> key
    }

    private fun normalizeDrmScheme(value: String): String = when (value.lowercase()) {
        "com.widevine.alpha", "widevine" -> "widevine"
        "com.microsoft.playready", "playready" -> "playready"
        else -> "clearkey"
    }

    const val HEADER_USER_AGENT = "User-Agent"
    const val HEADER_REFERER = "Referer"

    private class RawEntry {
        var title: String? = null
        var tvgId: String? = null
        var tvgName: String? = null
        var logo: String? = null
        var group: String? = null
        var lcn: Int? = null
        var url: String? = null
        val headers = LinkedHashMap<String, String>()
        var drmScheme: String? = null
        var drmLicense: String? = null
        var streamType: String? = null
    }
}
