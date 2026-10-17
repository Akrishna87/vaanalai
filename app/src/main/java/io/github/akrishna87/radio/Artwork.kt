package io.github.akrishna87.radio

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The picture to show for a station when no song cover is showing: the user's own picture, the
 * directory's logo, or one found on the station's website.
 */
fun Station.artworkUrl(): String? =
    image.takeIf { it.isNotEmpty() }
        ?: favicon.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        ?: WebsiteIcons.known(homepage)

/** Text handling for artwork, kept free of Android classes so it can be unit-tested. */
object ArtText {
    private val NOT_SONGS = listOf("http", "www.", ".com", ".in/", "advert", "commercial", "jingle", "unknown", "station id")

    /**
     * What to search for, from what a station says is playing ("Artist - Title (Radio Edit)" →
     * "Artist Title"), or null when it doesn't look like a song.
     */
    fun searchTerms(streamTitle: String, stationName: String = ""): String? {
        val t = streamTitle.trim()
        if (t.length < 3) return null
        val lower = t.lowercase()
        if (stationName.isNotBlank() && lower == stationName.trim().lowercase()) return null
        if (NOT_SONGS.any { it in lower }) return null
        val cleaned = stripBrackets(t)
            .replace(Regex("""(?i)\b(ft|feat|featuring)\b\.?"""), " ")
            .replace(Regex("""[-–—_|/:~*]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return cleaned.takeIf { it.length >= 3 && it.any(Char::isLetter) }
    }

    /**
     * Whether a search result really is the song on air: its title must appear in the station's
     * text, and so must its artist when the station names one ("Artist - Title").
     */
    fun matches(streamTitle: String, artist: String, track: String): Boolean {
        val stream = normalize(streamTitle)
        val title = normalize(stripBrackets(track))
        if (title.length < 2 || title !in stream) return false
        if (!streamTitle.contains(" - ")) return true
        val firstArtistWord = normalize(artist).split(' ').firstOrNull { it.length >= 2 } ?: return true
        return firstArtistWord in stream
    }

    /** iTunes artwork links end in "100x100bb.jpg"; ask for a sharper one. */
    fun biggerArtwork(url: String): String = url.replace(Regex("""/\d+x\d+bb\."""), "/600x600bb.")

    /**
     * Logo links found in a web page, best first: apple-touch-icon, then large icons, then the
     * page's share image, then the usual /apple-touch-icon.png.
     */
    fun iconLinks(html: String, pageUrl: String): List<String> {
        val head = html.take(200_000)
        val touch = mutableListOf<String>()
        val icons = mutableListOf<Pair<Int, String>>()
        val og = mutableListOf<String>()
        for (tag in Regex("""<(link|meta)\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(head)) {
            val text = tag.value
            fun attr(name: String) = Regex("""\b$name\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
                .find(text)?.let { m -> m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } } }
            if (tag.groupValues[1].equals("link", true)) {
                val rel = attr("rel")?.lowercase() ?: continue
                val href = attr("href")?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("data:") } ?: continue
                when {
                    "apple-touch-icon" in rel -> touch += href
                    "icon" in rel.split(' ') -> {
                        // Small favicons look blurry blown up; keep big ones, and ones of unknown size to try.
                        val size = attr("sizes")?.substringBefore('x')?.toIntOrNull() ?: 0
                        if (size == 0 || size >= 96) icons += size to href
                    }
                }
            } else {
                val prop = (attr("property") ?: attr("name"))?.lowercase()
                if (prop == "og:image" || prop == "twitter:image") attr("content")?.trim()?.takeIf { it.isNotEmpty() }?.let { og += it }
            }
        }
        val ordered = touch + icons.sortedByDescending { it.first }.map { it.second } + og + listOf("/apple-touch-icon.png")
        return ordered.mapNotNull { resolve(pageUrl, it) }.filter { !it.endsWith(".svg", true) }.distinct()
    }

    private fun resolve(base: String, href: String): String? = runCatching {
        val u = URL(URL(base), href).toString()
        u.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }.getOrNull()

    private fun stripBrackets(s: String) = s.replace(Regex("""[(\[{][^)\]}]*[)\]}]"""), " ")

    private fun normalize(s: String) = s.lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

/** Album covers for the song a station says is playing, from Apple's free iTunes search. */
object CoverArt {
    /** Search terms → cover link ("" = looked, found none). */
    private val found = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 300
    }

    suspend fun find(streamTitle: String, stationName: String): String? {
        val terms = ArtText.searchTerms(streamTitle, stationName) ?: return null
        synchronized(found) { found[terms] }?.let { return it.ifEmpty { null } }
        val cover = withContext(Dispatchers.IO) {
            runCatching { search(terms, streamTitle) }.getOrElse { return@withContext null } // offline: try again next time
                .orEmpty()
        } ?: return null
        synchronized(found) { found[terms] = cover }
        return cover.ifEmpty { null }
    }

    private fun search(terms: String, streamTitle: String): String? {
        val url = "https://itunes.apple.com/search?media=music&entity=song&limit=5&term=" + URLEncoder.encode(terms, "UTF-8")
        val conn = URL(url).openConnection() as HttpURLConnection
        val text = try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", "Vaanalai/1.0 (Android)")
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val results = JSONObject(text).optJSONArray("results") ?: return null
        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: continue
            val art = r.optString("artworkUrl100").takeIf { it.startsWith("https://") } ?: continue
            if (ArtText.matches(streamTitle, r.optString("artistName"), r.optString("trackName"))) return ArtText.biggerArtwork(art)
        }
        return null
    }
}

/** Logos found on stations' own websites, remembered between runs. */
object WebsiteIcons {
    private const val NONE = "-"
    private const val RETRY_AFTER_MS = 14L * 24 * 60 * 60 * 1000
    @Volatile private var prefs: SharedPreferences? = null
    private val pages = Semaphore(3)

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("website_icons", Context.MODE_PRIVATE)
    }

    /** The logo already found for this website, or null (none found yet, or none there). */
    fun known(homepage: String): String? =
        prefs?.getString(homepage, null)?.takeIf { !it.startsWith(NONE) && it.isNotEmpty() }

    /** Whether the website was checked recently and had no usable logo. */
    fun knownMissing(homepage: String): Boolean {
        val v = prefs?.getString(homepage, null) ?: return false
        if (!v.startsWith(NONE)) return false
        val at = v.removePrefix(NONE).toLongOrNull() ?: 0L
        return System.currentTimeMillis() - at < RETRY_AFTER_MS
    }

    fun remember(homepage: String, iconUrl: String?) {
        prefs?.edit()?.putString(homepage, iconUrl ?: (NONE + System.currentTimeMillis()))?.apply()
    }

    /** Possible logo links on the station's website, best first. */
    suspend fun candidates(homepage: String): List<String> {
        if (!(homepage.startsWith("https://") || homepage.startsWith("http://"))) return emptyList()
        return pages.withPermit {
            withContext(Dispatchers.IO) {
                runCatching {
                    val (finalUrl, html) = fetchPage(homepage)
                    ArtText.iconLinks(html, finalUrl)
                }.getOrDefault(emptyList())
            }
        }
    }

    private fun fetchPage(start: String): Pair<String, String> {
        var url = start
        repeat(4) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Vaanalai/1.0")
                conn.setRequestProperty("Accept", "text/html")
                val code = conn.responseCode
                if (code in 300..399) {
                    url = URL(URL(url), conn.getHeaderField("Location") ?: throw IOException("redirect")).toString()
                    return@repeat
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                val text = conn.inputStream.bufferedReader().use { r ->
                    val buf = CharArray(200_000)
                    var n = 0
                    while (n < buf.size) {
                        val got = r.read(buf, n, buf.size - n)
                        if (got < 0) break
                        n += got
                    }
                    String(buf, 0, n)
                }
                return url to text
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }
}
