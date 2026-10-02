package io.github.akrishna87.radio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * The free, community-run station directory at https://www.radio-browser.info (about 50,000
 * stations). It runs on a few mirrors; if one is down the next is tried.
 */
object RadioBrowser {
    private const val USER_AGENT = "Vaanoli/1.0 (+https://github.com/Akrishna87/Akrishna87)"
    private val FALLBACK_HOSTS = listOf(
        "de1.api.radio-browser.info",
        "de2.api.radio-browser.info",
        "fi1.api.radio-browser.info",
        "nl1.api.radio-browser.info",
    )

    @Volatile private var hosts: List<String>? = null
    @Volatile private var goodHost: String? = null

    /** The mirrors to try, the last one that worked first. */
    private fun hostsToTry(): List<String> {
        val known = hosts ?: run {
            val listed = runCatching {
                val a = JSONArray(fetch("https://all.api.radio-browser.info/json/servers", timeoutMs = 5_000))
                (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("name") }
                    .filter { it.endsWith(".api.radio-browser.info") }
            }.getOrDefault(emptyList())
            (listed.shuffled() + FALLBACK_HOSTS.shuffled()).distinct().also { hosts = it }
        }
        val good = goodHost ?: return known
        return listOf(good) + (known - good)
    }

    private fun fetch(url: String, timeoutMs: Int = 10_000): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode} from $url")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun get(path: String, params: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val query = params.filterValues { it != null && it.toString().isNotEmpty() }
            .map { (k, v) -> "$k=${URLEncoder.encode(v.toString(), "UTF-8")}" }
            .joinToString("&")
        var last: Exception = IOException("No station directory server answered")
        for (host in hostsToTry().take(4)) {
            try {
                val text = fetch("https://$host/json/$path" + if (query.isEmpty()) "" else "?$query")
                goodHost = host
                return@withContext text
            } catch (e: Exception) {
                last = e
            }
        }
        throw last
    }

    private suspend fun stations(params: Map<String, Any?>): List<Station> {
        val text = get("stations/search", params + mapOf("hidebroken" to "true"))
        return withContext(Dispatchers.Default) {
            val a = JSONArray(text)
            (0 until a.length())
                .mapNotNull { a.optJSONObject(it)?.let(Station::fromRadioBrowser) }
                .distinctBy { it.name.lowercase(Locale.ROOT) }
        }
    }

    /** The most listened-to stations, in one country or ([countryCode] empty) worldwide. */
    suspend fun popular(countryCode: String, offset: Int = 0, limit: Int = 40): List<Station> = stations(
        mapOf(
            "countrycode" to countryCode,
            "order" to "clickcount",
            "reverse" to "true",
            "offset" to offset,
            "limit" to limit,
        ),
    )

    /** Stations with a tag such as "jazz" or "tamil", most popular first. */
    suspend fun byTag(tag: String, countryCode: String, offset: Int = 0, limit: Int = 40): List<Station> = stations(
        mapOf(
            "tag" to tag,
            "countrycode" to countryCode,
            "order" to "clickcount",
            "reverse" to "true",
            "offset" to offset,
            "limit" to limit,
        ),
    )

    /** Stations whose name matches, then ones tagged with the words, most popular first. */
    suspend fun search(query: String, countryCode: String): List<Station> = coroutineScope {
        val common = mapOf(
            "countrycode" to countryCode,
            "order" to "clickcount",
            "reverse" to "true",
            "limit" to 60,
        )
        val byName = async { stations(common + mapOf("name" to query)) }
        val byTag = async { runCatching { stations(common + mapOf("tag" to query.trim().lowercase(Locale.ROOT), "limit" to 30)) }.getOrDefault(emptyList()) }
        (byName.await() + byTag.await()).distinctBy { it.name.lowercase(Locale.ROOT) }
    }

    /** Countries that have working stations, those with the most first. */
    suspend fun countries(): List<Country> {
        val text = get("countries", mapOf("order" to "stationcount", "reverse" to "true", "hidebroken" to "true"))
        return withContext(Dispatchers.Default) {
            val a = JSONArray(text)
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val code = o.optString("iso_3166_1").uppercase(Locale.ROOT)
                val count = o.optInt("stationcount")
                if (code.length != 2 || count <= 0) return@mapNotNull null
                Country(code, Station.countryName(code).ifEmpty { o.optString("name") }, count)
            }.distinctBy { it.code }
        }
    }

    /**
     * Tells the directory a station was played. Radio Browser asks apps to do this; it keeps the
     * "most popular" order meaningful. Best effort only.
     */
    suspend fun countPlay(stationId: String) {
        if (stationId.startsWith(Station.CUSTOM_PREFIX)) return
        runCatching { get("url/${URLEncoder.encode(stationId, "UTF-8")}", emptyMap()) }
    }
}
