package io.github.nanobuo.myiptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {

    private fun parse(text: String, ua: String? = null) = M3uParser.parse(text.byteInputStream().bufferedReader(), ua)

    @Test
    fun parsesHeaderEpgAndUserAgent() {
        val p = parse(
            """
            #EXTM3U url-tvg="http://a/epg.xml.gz,http://b/epg.xml" user-agent="HeaderUA/1.0"
            #EXTINF:-1 tvg-id="rai1.it" tvg-logo="http://logo/r1.png" group-title="Rai" tvg-chno="1",Rai 1 HD
            http://stream/rai1.m3u8
            """.trimIndent()
        )
        assertEquals(listOf("http://a/epg.xml.gz", "http://b/epg.xml"), p.epgUrls)
        val ch = p.channels.single()
        assertEquals("Rai 1 HD", ch.name)
        assertEquals(1, ch.number)
        assertEquals("rai1.it", ch.tvgId)
        assertEquals("Rai", ch.group)
        assertEquals("HeaderUA/1.0", ch.headers["User-Agent"])
    }

    @Test
    fun sortsByLcnAndNumbersTheRest() {
        val p = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-chno="5",Canale 5
            http://s/5
            #EXTINF:-1,Senza numero
            http://s/x
            #EXTINF:-1 tvg-chno="2",Rai 2
            http://s/2
            """.trimIndent()
        )
        assertEquals(listOf("Rai 2", "Canale 5", "Senza numero"), p.channels.map { it.name })
        assertEquals(listOf(2, 5, 6), p.channels.map { it.number })
    }

    @Test
    fun channelUserAgentOverridesHeader() {
        val p = parse(
            """
            #EXTM3U http-user-agent="Header"
            #EXTINF:-1 tvg-name="A, B",Title, with comma
            #EXTVLCOPT:http-user-agent=VlcOpt
            #EXTVLCOPT:http-referrer=http://ref/
            http://s/a
            #EXTINF:-1 user-agent="Attr",Second
            http://s/b|User-Agent=Pipe%20UA&Referer=http://r2/
            #EXTINF:-1,Third
            #EXTHTTP:{"User-Agent":"JsonUA","cookie":"a=b"}
            http://s/c
            """.trimIndent()
        )
        val (a, b, c) = p.channels
        assertEquals("Title, with comma", a.name)
        assertEquals("VlcOpt", a.headers["User-Agent"])
        assertEquals("http://ref/", a.headers["Referer"])
        assertEquals("http://s/b", b.url)
        assertEquals("Pipe UA", b.headers["User-Agent"])
        assertEquals("http://r2/", b.headers["Referer"])
        assertEquals("JsonUA", c.headers["User-Agent"])
        assertEquals("a=b", c.headers["Cookie"])
    }

    @Test
    fun parsesKodiDrmProps() {
        val p = parse(
            """
            #EXTM3U
            #EXTINF:-1 group-title="X",Drm
            #KODIPROP:inputstream.adaptive.manifest_type=mpd
            #KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey
            #KODIPROP:inputstream.adaptive.license_key=0123:4567
            http://s/manifest.mpd
            #EXTINF:-1,Plain
            http://s/plain.ts
            """.trimIndent()
        )
        val drm = p.channels[0].drm!!
        assertEquals("clearkey", drm.scheme)
        assertEquals("0123:4567", drm.license)
        assertEquals("dash", p.channels[0].streamType)
        assertNull(p.channels[1].drm)
    }

    @Test
    fun fallbackUserAgentIsUsedWhenPlaylistHasNone() {
        val p = parse("#EXTM3U\n#EXTINF:-1,A\nhttp://s/a", ua = "Custom")
        assertEquals("Custom", p.channels[0].headers["User-Agent"])
    }
}
