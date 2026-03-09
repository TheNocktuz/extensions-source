package eu.kanade.tachiyomi.animeextension.es.animeav1

import android.app.Application
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class AnimeAV1 : ParsedAnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "AnimeAV1"
    override val baseUrl = "https://animeav1.com"
    override val lang = "es"
    override val supportsLatest = true

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // ============================== Popular ==============================

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/catalogo?page=$page&order=visitas", headers)

    override fun popularAnimeSelector(): String =
        "div.anime-card, article.anime-item, div.item-anime, div.anime"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
            title = element.selectFirst("h3, h2, .title, .anime-title")?.text()
                ?: element.selectFirst("a")?.attr("title") ?: ""
            thumbnail_url = element.selectFirst("img")?.let {
                it.attr("data-src").ifEmpty { it.attr("src") }
            }
        }
    }

    override fun popularAnimeNextPageSelector(): String =
        "a[rel=next], .pagination .next, li.page-item.active + li a, a.next"

    // ============================== Latest ==============================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/catalogo?page=$page&order=recientes", headers)

    override fun latestUpdatesSelector(): String = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Search ==============================

    override fun getFilterList(): AnimeFilterList = AnimeAV1Filters.FILTER_LIST

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return if (query.startsWith(AnimeAV1UrlActivity.PREFIX_SEARCH)) {
            val slug = query.removePrefix(AnimeAV1UrlActivity.PREFIX_SEARCH)
            GET("$baseUrl/anime/$slug", headers)
        } else if (query.isNotBlank()) {
            val url = "$baseUrl/buscar".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()
            GET(url, headers)
        } else {
            val filterParams = AnimeAV1Filters.getSearchParameters(filters)
            val url = "$baseUrl/catalogo".toHttpUrl().newBuilder().apply {
                addQueryParameter("page", page.toString())
                addQueryParameter("order", filterParams.order)
                if (filterParams.genre.isNotEmpty()) addQueryParameter("genero", filterParams.genre)
                if (filterParams.year.isNotEmpty()) addQueryParameter("anyo", filterParams.year)
                if (filterParams.status.isNotEmpty()) addQueryParameter("estado", filterParams.status)
                if (filterParams.type.isNotEmpty()) addQueryParameter("tipo", filterParams.type)
            }.build()
            GET(url, headers)
        }
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()
    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Details ==============================

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("h1.anime-title, h1.entry-title, h1")?.text() ?: ""
            thumbnail_url = document.selectFirst(
                "div.anime-image img, div.poster img, .cover img, img.poster",
            )?.let { it.attr("data-src").ifEmpty { it.attr("src") } }
            description = document.selectFirst(
                "div.sinopsis p, div.description, p.synopsis, .anime-synopsis",
            )?.text()
            genre = document.select(
                "div.genres a, .genre-list a, a.genre, span.genre",
            ).joinToString(", ") { it.text() }
            author = document.selectFirst("span.studio a, .studio a, .produccion a")?.text()
            status = parseStatus(
                document.selectFirst("span.estado, .status span, .anime-status")?.text(),
            )
        }
    }

    private fun parseStatus(status: String?): Int = when {
        status == null -> SAnime.UNKNOWN
        status.contains("En emisión", ignoreCase = true) ||
            status.contains("En curso", ignoreCase = true) -> SAnime.ONGOING
        status.contains("Finalizado", ignoreCase = true) -> SAnime.COMPLETED
        else -> SAnime.UNKNOWN
    }

    // ============================== Episodes ==============================

    override fun episodeListSelector(): String =
        "ul.episodes-list li, div.episodes-list div.ep-item, ul.episodios li, div.episodios a, #episodios .ep"

    override fun episodeFromElement(element: Element): SEpisode {
        return SEpisode.create().apply {
            val link = element.selectFirst("a") ?: element
            setUrlWithoutDomain(link.attr("href"))
            name = element.selectFirst(".ep-title, .episode-title, span.num, .ep-name")
                ?.text() ?: link.text()
            date_upload = element.selectFirst(".ep-date, .date, span.date, .fecha")
                ?.text()?.let { parseDate(it) } ?: 0L
            episode_number = element.selectFirst(".ep-num, .num, span.episode-number")
                ?.text()?.replace(Regex("[^\\d.]"), "")?.toFloatOrNull()
                ?: name.replace(Regex("[^\\d.]"), "").toFloatOrNull() ?: 0F
        }
    }

    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale("es", "MX"))

    private fun parseDate(dateStr: String): Long =
        runCatching { dateFormatter.parse(dateStr.trim())?.time }.getOrNull() ?: 0L

    // ============================== Video URLs ==============================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        // Try embedded iframes
        document.select("iframe[src], iframe[data-src]").forEach { iframe ->
            val src = iframe.attr("data-src").ifEmpty { iframe.attr("src") }
            if (src.isNotEmpty()) runCatching { videoList.addAll(extractVideosFromEmbed(src)) }
        }

        // Try direct <video> tags
        document.select("video source[src], video[src]").forEach { video ->
            val src = video.attr("src")
            if (src.isNotEmpty()) videoList.add(Video(src, "Directo", src))
        }

        // Fallback: parse from scripts
        if (videoList.isEmpty()) {
            val scripts = document.select("script").joinToString("\n") { it.data() }
            val urlRegex = Regex("""['"](\bhttps?://[^'"]+\.(?:mp4|m3u8)[^'"]*)['"]""")
            urlRegex.findAll(scripts).forEach { match ->
                val url = match.groupValues[1]
                videoList.add(Video(url, detectQuality(url), url))
            }
        }

        return videoList.sortedWith(
            compareByDescending<Video> { it.quality.contains(preferredQuality) }
                .thenByDescending { it.quality },
        )
    }

    private fun detectQuality(url: String): String = when {
        url.contains("1080") -> "1080p"
        url.contains("720") -> "720p"
        url.contains("480") -> "480p"
        url.contains("360") -> "360p"
        else -> "Video"
    }

    private fun extractVideosFromEmbed(embedUrl: String): List<Video> {
        val videos = mutableListOf<Video>()
        return runCatching {
            val resp = client.newCall(GET(embedUrl, headers)).execute()
            val body = resp.body.string()
            val urlRegex = Regex("""['"](\bhttps?://[^'"]+\.(?:mp4|m3u8)[^'"]*)['"]""")
            urlRegex.findAll(body).forEach { match ->
                val url = match.groupValues[1]
                videos.add(Video(url, detectQuality(url), url))
            }
            videos
        }.getOrElse { videos }
    }

    override fun videoListSelector(): String = "iframe[src], iframe[data-src], video source"

    override fun videoFromElement(element: Element): Video {
        val src = element.attr("data-src").ifEmpty { element.attr("src") }
        return Video(src, detectQuality(src), src)
    }

    override fun videoUrlParse(document: Document): String =
        document.selectFirst("video source, video")?.let {
            it.attr("src").ifEmpty { it.attr("data-src") }
        } ?: ""

    // ============================== Preferences ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Calidad preferida"
            entries = arrayOf("1080p", "720p", "480p", "360p")
            entryValues = arrayOf("1080", "720", "480", "360")
            setDefaultValue("720")
            summary = "%s"
        }.also(screen::addPreference)
    }

    private val preferredQuality: String
        get() = preferences.getString(PREF_QUALITY_KEY, "720")!!

    companion object {
        private const val PREF_QUALITY_KEY = "preferred_quality"
    }
}
