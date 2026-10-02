package io.github.akrishna87.radio

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** One radio station, from the Radio Browser directory or added by hand. */
data class Station(
    val id: String,
    val name: String,
    /** The stream itself (never a .pls/.m3u playlist). */
    val url: String,
    val homepage: String = "",
    val favicon: String = "",
    val tags: List<String> = emptyList(),
    val country: String = "",
    val countryCode: String = "",
    val language: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val hls: Boolean = false,
) {
    /** Added by hand with a stream link, rather than found in the directory. */
    val isCustom: Boolean get() = id.startsWith(CUSTOM_PREFIX)

    /** "🇮🇳 India · Tamil, Film · 128 kbps" */
    val subtitle: String
        get() = listOfNotNull(
            countryLabel().ifEmpty { null },
            tags.take(3).joinToString(", ") { it.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } }.ifEmpty { null },
            quality().ifEmpty { null },
        ).joinToString(" · ").ifEmpty { if (isCustom) "Added by you" else "" }

    fun countryLabel(): String {
        val name = countryName(countryCode).ifEmpty { country }
        val flag = flag(countryCode)
        return listOf(flag, name).filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun quality(): String = when {
        bitrate > 0 && codec.isNotEmpty() && codec != "UNKNOWN" -> "$bitrate kbps $codec"
        bitrate > 0 -> "$bitrate kbps"
        codec.isNotEmpty() && codec != "UNKNOWN" -> codec
        else -> ""
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("url", url)
        put("homepage", homepage)
        put("favicon", favicon)
        put("tags", JSONArray(tags))
        put("country", country)
        put("cc", countryCode)
        put("language", language)
        put("codec", codec)
        put("bitrate", bitrate)
        put("hls", hls)
    }

    companion object {
        const val CUSTOM_PREFIX = "custom:"

        fun fromJson(o: JSONObject): Station? {
            val id = o.optString("id")
            val url = o.optString("url")
            if (id.isEmpty() || url.isEmpty()) return null
            val tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            return Station(
                id = id,
                name = o.optString("name").ifEmpty { url },
                url = url,
                homepage = o.optString("homepage"),
                favicon = o.optString("favicon"),
                tags = tags.filter { it.isNotBlank() },
                country = o.optString("country"),
                countryCode = o.optString("cc"),
                language = o.optString("language"),
                codec = o.optString("codec"),
                bitrate = o.optInt("bitrate"),
                hls = o.optBoolean("hls"),
            )
        }

        fun fromJson(text: String?): Station? =
            text?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }

        fun listFromJson(text: String?): List<Station> {
            if (text.isNullOrEmpty()) return emptyList()
            return runCatching {
                val a = JSONArray(text)
                (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::fromJson) }
            }.getOrDefault(emptyList())
        }

        fun listToJson(list: List<Station>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        /** A station as Radio Browser describes it, or null if it has no stream. */
        fun fromRadioBrowser(o: JSONObject): Station? {
            val id = o.optString("stationuuid")
            val url = o.optString("url_resolved").ifBlank { o.optString("url") }.trim()
            if (id.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) return null
            return Station(
                id = id,
                name = o.optString("name").trim().ifEmpty { url },
                url = url,
                homepage = o.optString("homepage").trim(),
                favicon = o.optString("favicon").trim(),
                tags = o.optString("tags").split(',').map { it.trim() }.filter { it.isNotEmpty() && it.length < 30 }.distinct().take(5),
                country = o.optString("country").trim(),
                countryCode = o.optString("countrycode").trim().uppercase(Locale.ROOT),
                language = o.optString("language").trim(),
                codec = o.optString("codec").trim().uppercase(Locale.ROOT),
                bitrate = o.optInt("bitrate"),
                hls = o.optInt("hls") == 1,
            )
        }

        /** The country's flag emoji from its two-letter code ("IN" → 🇮🇳). */
        fun flag(countryCode: String): String {
            if (countryCode.length != 2 || !countryCode.all { it in 'A'..'Z' }) return ""
            return countryCode.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
        }

        /** The country's name in the phone's language ("IN" → "India"). */
        fun countryName(countryCode: String): String {
            if (countryCode.length != 2) return ""
            val name = Locale("", countryCode).getDisplayCountry(Locale.getDefault())
            return if (name.isEmpty() || name.equals(countryCode, ignoreCase = true)) "" else name
        }
    }
}

/** A country in the directory, with how many stations it has. */
data class Country(val code: String, val name: String, val stations: Int) {
    val label: String get() = listOf(Station.flag(code), name).filter { it.isNotEmpty() }.joinToString(" ")
}
