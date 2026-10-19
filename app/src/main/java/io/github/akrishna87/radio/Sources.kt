package io.github.akrishna87.radio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** All India Radio's own live streams, listed when the app was built (from akashvani.gov.in). */
object AllIndiaRadio {
    const val PREFIX = "air:"
    @Volatile private var all: List<Station>? = null

    fun init(context: Context) {
        if (all != null) return
        all = runCatching {
            val text = context.applicationContext.assets.open("air_stations.json").bufferedReader().use { it.readText() }
            val a = JSONArray(text)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Station(
                    id = PREFIX + o.getString("id"),
                    name = o.getString("name"),
                    url = o.getString("url"),
                    homepage = "https://akashvani.gov.in/",
                    favicon = o.optString("image"),
                    tags = listOf("all india radio") + listOfNotNull(o.optString("group").ifBlank { null }),
                    country = "India",
                    countryCode = "IN",
                    hls = true,
                )
            }
        }.getOrDefault(emptyList())
    }

    fun stations(): List<Station> = all.orEmpty()

    /** Stations whose name or group has every word of [query] ("chennai", "vividh bharati"). */
    fun search(query: String): List<Station> {
        val words = query.lowercase(Locale.ROOT).split(Regex("""\W+""")).filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList()
        return stations().filter { s ->
            val text = (s.name + " " + s.tags.joinToString(" ") + " air akashvani").lowercase(Locale.ROOT)
            words.all { it in text }
        }
    }
}

/** Zeno.fm, where many small Indian and community stations stream. */
object ZenoFm {
    const val PREFIX = "zeno:"
    private const val API = "https://zeno.fm/api/stations/"

    /** Stations matching [query] that have a stream link, from [countryCode] only when it's set. */
    suspend fun search(query: String, countryCode: String, limit: Int = 15): List<Station> = coroutineScope {
        val found = JSONObject(get(API + "?limit=$limit&query=" + URLEncoder.encode(query, "UTF-8"))).optJSONArray("data")
            ?: return@coroutineScope emptyList()
        val ids = (0 until found.length()).mapNotNull { found.optJSONObject(it)?.optString("id")?.ifEmpty { null } }
        // The search doesn't include stream links, so each station is looked up (in parallel).
        ids.map { id -> async { runCatching { station(id) }.getOrNull() } }.awaitAll()
            .filterNotNull()
            .filter { countryCode.isEmpty() || it.countryCode == countryCode }
    }

    private suspend fun station(id: String): Station? {
        val o = JSONObject(get(API + URLEncoder.encode(id, "UTF-8") + "/"))
        val url = o.optString("streamURL").takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
        val country = o.optString("country")
        return Station(
            id = PREFIX + id,
            name = o.optString("title").trim().ifEmpty { return null },
            url = url,
            homepage = o.optString("slug").ifEmpty { null }?.let { "https://zeno.fm/radio/$it/" } ?: "",
            favicon = o.optString("metaLogo").takeIf { it.startsWith("https://") }
                ?: o.optJSONObject("logoImage")?.optString("src").orEmpty(),
            tags = listOf(o.optString("genre")).filter { it.isNotBlank() && it != "General" }.map { it.lowercase(Locale.ROOT) },
            country = country,
            countryCode = countryCode(country),
            language = o.optJSONArray("languages")?.optString(0).orEmpty(),
            hls = url.substringBefore('?').endsWith(".m3u8"),
        )
    }

    private fun countryCode(name: String): String =
        if (name.isBlank()) "" else Locale.getISOCountries().firstOrNull {
            Locale("", it).getDisplayCountry(Locale.ENGLISH).equals(name, ignoreCase = true)
        }.orEmpty()

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", "Vaanalai/1.0 (Android)")
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode} from Zeno.fm")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
