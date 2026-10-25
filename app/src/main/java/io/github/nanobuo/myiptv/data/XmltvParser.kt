package io.github.nanobuo.myiptv.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.text.Normalizer

/**
 * Streaming XMLTV parser. Only programmes belonging to channels of the playlist are kept,
 * within a time window around "now", to limit memory usage on TV devices.
 */
object XmltvParser {

    fun parse(
        input: InputStream,
        channels: List<Channel>,
        now: Long = System.currentTimeMillis(),
        keepPastMs: Long = 6 * HOUR,
        keepFutureMs: Long = 4 * DAY,
    ): Map<String, MutableList<Programme>> {
        val byTvgId = HashMap<String, MutableList<String>>()
        val byName = HashMap<String, MutableList<String>>()
        channels.forEach { ch ->
            ch.tvgId?.let { byTvgId.getOrPut(it.lowercase()) { mutableListOf() } += ch.key }
            byName.getOrPut(normalizeName(ch.tvgName ?: ch.name)) { mutableListOf() } += ch.key
            if (ch.tvgName != null) byName.getOrPut(normalizeName(ch.name)) { mutableListOf() } += ch.key
        }
        // Channels with a tvg-id are matched only by id; the others by name.
        val keysWithId = channels.filter { it.tvgId != null }.mapTo(HashSet()) { it.key }

        val xmlIdToKeys = HashMap<String, List<String>>()
        val result = HashMap<String, MutableList<Programme>>()
        val minStop = now - keepPastMs
        val maxStart = now + keepFutureMs

        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
        parser.setInput(input, null)

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val names = readChannelNames(parser)
                        if (id != null) {
                            val keys = LinkedHashSet<String>()
                            byTvgId[id.lowercase()]?.let(keys::addAll)
                            (names + id).forEach { n ->
                                byName[normalizeName(n)]?.filterTo(keys) { it !in keysWithId }
                            }
                            if (keys.isNotEmpty()) xmlIdToKeys[id] = keys.toList()
                        }
                    }

                    "programme" -> {
                        val channelId = parser.getAttributeValue(null, "channel")
                        val keys = channelId?.let { xmlIdToKeys[it] ?: byTvgId[it.lowercase()] }
                        val start = parseTime(parser.getAttributeValue(null, "start"))
                        val stop = parseTime(parser.getAttributeValue(null, "stop"))
                        if (keys == null || start == null || start > maxStart || (stop != null && stop < minStop)) {
                            skip(parser)
                        } else {
                            val p = readProgramme(parser, start, stop ?: NO_STOP)
                            keys.forEach { result.getOrPut(it) { mutableListOf() } += p }
                        }
                    }
                }
            }
            event = parser.next()
        }

        result.values.forEach { list ->
            list.sortBy { it.start }
            // Programmes without an explicit stop time end when the next one starts.
            for (i in list.indices) {
                val p = list[i]
                if (p.stop != NO_STOP) continue
                val nextStart = list.getOrNull(i + 1)?.start?.takeIf { it > p.start }
                list[i] = p.copy(stop = nextStart ?: (p.start + HOUR))
            }
        }
        return result
    }

    private fun readChannelNames(parser: XmlPullParser): List<String> {
        val names = mutableListOf<String>()
        val depth = parser.depth
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.depth == depth) && event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "display-name") {
                names += readText(parser)
            }
            event = parser.next()
        }
        return names
    }

    private fun readProgramme(parser: XmlPullParser, start: Long, stop: Long): Programme {
        var title: String? = null
        var titleLangMatches = false
        var desc: String? = null
        var descLangMatches = false
        var category: String? = null
        val preferredLang = java.util.Locale.getDefault().language
        val depth = parser.depth
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.depth == depth) && event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val lang = parser.getAttributeValue(null, "lang")
                val langMatches = lang != null && lang.startsWith(preferredLang, ignoreCase = true)
                when (parser.name) {
                    "title" -> {
                        val text = readText(parser)
                        if (title == null || (langMatches && !titleLangMatches)) {
                            title = text
                            titleLangMatches = langMatches
                        }
                    }
                    "desc" -> {
                        val text = readText(parser)
                        if (desc == null || (langMatches && !descLangMatches)) {
                            desc = text
                            descLangMatches = langMatches
                        }
                    }
                    "category" -> {
                        val text = readText(parser)
                        if (category == null) category = text
                    }
                    else -> skip(parser)
                }
            }
            event = parser.next()
        }
        return Programme(start, stop, title?.trim().orEmpty(), desc?.trim()?.takeIf { it.isNotEmpty() }, category?.trim())
    }

    /** Reads the text of the current element and positions the parser on its END_TAG. */
    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        val depth = parser.depth
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.depth == depth) && event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.TEXT) sb.append(parser.text)
            event = parser.next()
        }
        return sb.toString().trim()
    }

    /** Skips the current element, positioning the parser on its END_TAG. */
    private fun skip(parser: XmlPullParser) {
        val depth = parser.depth
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.depth == depth) && event != XmlPullParser.END_DOCUMENT) {
            event = parser.next()
        }
    }

    /** Parses XMLTV dates like `20240131203000 +0100`. Dates without offset are treated as UTC. */
    internal fun parseTime(value: String?): Long? {
        if (value == null) return null
        val v = value.trim()
        if (v.length < 12) return null
        return try {
            val year = v.substring(0, 4).toInt()
            val month = v.substring(4, 6).toInt()
            val day = v.substring(6, 8).toInt()
            val hour = v.substring(8, 10).toInt()
            val minute = v.substring(10, 12).toInt()
            val second = if (v.length >= 14 && v[12].isDigit()) v.substring(12, 14).toInt() else 0
            val tz = v.substring(minOf(v.length, 12)).trimStart { it.isDigit() }.trim()
            val zone: ZoneId = when {
                tz.isEmpty() -> ZoneOffset.UTC
                tz.length >= 5 && (tz[0] == '+' || tz[0] == '-') -> {
                    val sign = if (tz[0] == '-') -1 else 1
                    val h = tz.substring(1, 3).toInt()
                    val m = tz.substring(3, 5).toInt()
                    ZoneOffset.ofHoursMinutes(sign * h, sign * m)
                }
                else -> runCatching { ZoneId.of(tz) }.getOrDefault(ZoneOffset.UTC)
            }
            LocalDateTime.of(year, month, day, hour, minute, second).atZone(zone).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    /** Lowercase, without accents, spaces, punctuation and common quality suffixes. */
    internal fun normalizeName(name: String): String {
        var n = Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        n = n.replace(Regex("""\b(fhd|uhd|hd|sd|4k|hevc|h265|backup|\.it|it)\b"""), " ")
        return n.replace(Regex("[^a-z0-9+]"), "")
    }

    private const val NO_STOP = Long.MIN_VALUE
    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR
}
