package com.darkiptv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.newExtractorLink

data class Channel(
    val name: String = "",
    val url: String = "",
    val logo: String? = null,
    val group: String? = null,
    val referer: String? = null,
    val userAgent: String? = null
)

class DarkIptvProvider : MainAPI() {

    // !!! KENDI GITHUB ADRESINLE DEGISTIR (kurulum araci otomatik yapar) !!!
    override var mainUrl = "https://raw.githubusercontent.com/darknesslord19/Dark-iptv/main/channels.json"
    override var name = "Dark IPTV"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    /** channels.json'u okur. url'si .m3u olan satirlar, playlist olarak acilip kanallara bolunur. */
    private suspend fun fetchChannels(): List<Channel> {
        val list = parseJson<List<Channel>>(app.get(mainUrl).text)
        val out = ArrayList<Channel>()
        for (c in list) {
            if (c.url.isBlank()) continue
            val isPlaylist = c.url.substringBefore("?").endsWith(".m3u", ignoreCase = true)
            if (isPlaylist) {
                try {
                    out.addAll(parseM3u(app.get(c.url).text, c))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else out.add(c)
        }
        return out
    }

    private fun parseM3u(text: String, parent: Channel): List<Channel> {
        val result = ArrayList<Channel>()
        var name: String? = null
        var logo: String? = null
        var group: String? = null
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                name = line.substringAfterLast(",").trim()
                logo = Regex("""tvg-logo="([^"]*)"""").find(line)?.groupValues?.get(1)
                group = Regex("""group-title="([^"]*)"""").find(line)?.groupValues?.get(1)
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                result.add(
                    Channel(
                        name = name?.ifBlank { null } ?: line,
                        url = line,
                        logo = logo?.ifBlank { null },
                        group = group?.ifBlank { null } ?: parent.group ?: parent.name,
                        referer = parent.referer,
                        userAgent = parent.userAgent
                    )
                )
                name = null; logo = null; group = null
            }
        }
        return result
    }

    private suspend fun Channel.toSearch(): SearchResponse =
        newLiveSearchResponse(name, encode(this), TvType.Live) { posterUrl = logo }

    // Kanal bilgisi url alaninda JSON olarak tasinir
    private fun encode(c: Channel): String = com.fasterxml.jackson.module.kotlin
        .jacksonObjectMapper().writeValueAsString(c)

    private fun decode(s: String): Channel = parseJson<Channel>(s)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val all = fetchChannels()
        val sections = all.groupBy { it.group ?: "Kanallar" }.map { (g, items) ->
            HomePageList(g, items.map { it.toSearch() }, isHorizontalImages = false)
        }
        return newHomePageResponse(sections, false)
    }

    override suspend fun search(query: String): List<SearchResponse> =
        fetchChannels()
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { it.toSearch() }

    override suspend fun load(url: String): LoadResponse {
        val c = decode(url)
        return newLiveStreamLoadResponse(c.name, url, url) {
            posterUrl = c.logo
            plot = c.group
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val c = decode(data)
        val isHls = c.url.contains(".m3u8", ignoreCase = true)
        callback(
            newExtractorLink(
                source = name,
                name = c.name,
                url = c.url,
                type = if (isHls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ) {
                this.referer = c.referer ?: ""
                this.quality = Qualities.Unknown.value
                c.userAgent?.let { this.headers = mapOf("User-Agent" to it) }
            }
        )
        return true
    }
}
