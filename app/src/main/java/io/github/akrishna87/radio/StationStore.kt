package io.github.akrishna87.radio

import android.content.Context
import android.content.SharedPreferences
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Favourites, recently played stations and the last queue, kept on the phone. Both the screens
 * and the playback service use it; screens watch [prefs] to see changes the service makes.
 */
class StationStore(context: Context) {
    private val appContext = context.applicationContext
    val prefs: SharedPreferences = appContext.getSharedPreferences("stations", Context.MODE_PRIVATE)

    fun favorites(): List<Station> = Station.listFromJson(prefs.getString(KEY_FAVORITES, null))

    fun recents(): List<Station> = Station.listFromJson(prefs.getString(KEY_RECENTS, null))

    fun isFavorite(id: String): Boolean = favorites().any { it.id == id }

    fun setFavorite(station: Station, favorite: Boolean) {
        val rest = favorites().filterNot { it.id == station.id }
        val list = if (favorite) rest + station else rest
        prefs.edit().putString(KEY_FAVORITES, Station.listToJson(list)).apply()
    }

    fun moveFavorite(from: Int, to: Int) {
        val list = favorites().toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        prefs.edit().putString(KEY_FAVORITES, Station.listToJson(list)).apply()
    }

    fun addRecent(station: Station) {
        val list = listOf(station) + recents().filterNot { it.id == station.id }
        prefs.edit().putString(KEY_RECENTS, Station.listToJson(list.take(MAX_RECENTS))).apply()
    }

    fun clearRecents() = prefs.edit().remove(KEY_RECENTS).apply()

    /** A station the app knows about by id: a favourite, a recent one, or in the last queue. */
    fun find(id: String): Station? =
        favorites().firstOrNull { it.id == id }
            ?: recents().firstOrNull { it.id == id }
            ?: lastQueue().first.firstOrNull { it.id == id }

    fun saveQueue(stations: List<Station>, index: Int) {
        prefs.edit()
            .putString(KEY_QUEUE, Station.listToJson(stations))
            .putInt(KEY_QUEUE_INDEX, index)
            .apply()
    }

    fun lastQueue(): Pair<List<Station>, Int> {
        val list = Station.listFromJson(prefs.getString(KEY_QUEUE, null))
        return list to prefs.getInt(KEY_QUEUE_INDEX, 0).coerceIn(0, (list.size - 1).coerceAtLeast(0))
    }

    /** The country whose stations Home shows; guessed from the SIM or phone settings at first. */
    var countryCode: String
        get() = prefs.getString(KEY_COUNTRY, null) ?: guessCountry()
        set(value) = prefs.edit().putString(KEY_COUNTRY, value).apply()

    private fun guessCountry(): String {
        val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val fromPhone = listOfNotNull(
            runCatching { tm?.networkCountryIso }.getOrNull(),
            runCatching { tm?.simCountryIso }.getOrNull(),
            Locale.getDefault().country,
        ).map { it.uppercase(Locale.ROOT) }.firstOrNull { it.length == 2 }
        return fromPhone ?: ""
    }

    companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENTS = "recents"
        const val KEY_QUEUE = "queue"
        const val KEY_QUEUE_INDEX = "queueIndex"
        const val KEY_COUNTRY = "country"
        /** When the sleep timer stops playback (ms since epoch), or 0. Written by the service. */
        const val KEY_SLEEP_UNTIL = "sleepUntil"
        private const val MAX_RECENTS = 30
    }
}
