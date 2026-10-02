package io.github.akrishna87.radio

import android.app.Application
import android.content.ComponentName
import android.content.SharedPreferences
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

enum class Tab(val label: String) { HOME("Home"), SEARCH("Search"), FAVORITES("Favourites") }

/** A genre or language to browse, and the directory tag behind it. */
data class Genre(val label: String, val tag: String)

val GENRES = listOf(
    Genre("Tamil", "tamil"),
    Genre("Hindi", "hindi"),
    Genre("Bollywood", "bollywood"),
    Genre("Malayalam", "malayalam"),
    Genre("Telugu", "telugu"),
    Genre("Kannada", "kannada"),
    Genre("Carnatic", "carnatic"),
    Genre("Devotional", "devotional"),
    Genre("News", "news"),
    Genre("Talk", "talk"),
    Genre("Pop", "pop"),
    Genre("Rock", "rock"),
    Genre("Jazz", "jazz"),
    Genre("Classical", "classical"),
    Genre("Dance", "dance"),
    Genre("Electronic", "electronic"),
    Genre("Hip hop", "hip hop"),
    Genre("Lo-fi", "lofi"),
    Genre("Chill", "chillout"),
    Genre("Oldies", "oldies"),
    Genre("80s", "80s"),
    Genre("90s", "90s"),
    Genre("Country", "country"),
    Genre("Sports", "sports"),
    Genre("Kids", "kids"),
)

/** A list of stations being fetched from the directory. */
data class StationList(
    val stations: List<Station> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val canLoadMore: Boolean = false,
)

class RadioViewModel(app: Application) : AndroidViewModel(app) {

    val store = StationStore(app)

    // ----- Navigation -----
    var tab by mutableStateOf(Tab.HOME)
    /** The genre page open on top of Home, if any. */
    var genre by mutableStateOf<Genre?>(null); private set
    var showPlayer by mutableStateOf(false)
    var showCountries by mutableStateOf(false)
    var showAddStation by mutableStateOf(false)
    var showSleep by mutableStateOf(false)
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    // ----- Saved stations -----
    var favorites by mutableStateOf(store.favorites()); private set
    var recents by mutableStateOf(store.recents()); private set
    var countryCode by mutableStateOf(store.countryCode); private set
    var sleepUntil by mutableLongStateOf(store.prefs.getLong(StationStore.KEY_SLEEP_UNTIL, 0L)); private set
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            StationStore.KEY_FAVORITES -> favorites = store.favorites()
            StationStore.KEY_RECENTS -> recents = store.recents()
            StationStore.KEY_SLEEP_UNTIL -> sleepUntil = p.getLong(StationStore.KEY_SLEEP_UNTIL, 0L)
        }
    }

    // ----- The directory -----
    var popular by mutableStateOf(StationList()); private set
    var worldwide by mutableStateOf(StationList()); private set
    var genreList by mutableStateOf(StationList()); private set
    /** The genre page shows the whole world rather than just [countryCode]. */
    var genreWorldwide by mutableStateOf(false); private set
    var countries by mutableStateOf<List<Country>>(emptyList()); private set
    var countriesError by mutableStateOf(false); private set

    var query by mutableStateOf(""); private set
    var searchInCountry by mutableStateOf(false); private set
    var results by mutableStateOf(StationList()); private set
    private var searchJob: Job? = null
    private var popularJob: Job? = null
    private var genreJob: Job? = null

    // ----- Playing now -----
    var current by mutableStateOf<Station?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    /** Connecting or buffering while it's meant to be playing. */
    var isLoading by mutableStateOf(false); private set
    var playWhenReady by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    /** The song the station says it's playing, when it says. */
    var nowPlaying by mutableStateOf<String?>(null); private set
    var queueSize by mutableStateOf(0); private set

    private var controller: MediaController? = null
    private val controllerFuture = MediaController.Builder(
        app,
        SessionToken(app, ComponentName(app, PlaybackService::class.java)),
    ).buildAsync()
    private var pending: (() -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncFromPlayer()
    }

    init {
        store.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        viewModelScope.launch {
            val c = try { controllerFuture.await() } catch (e: Exception) { return@launch }
            controller = c
            c.addListener(playerListener)
            syncFromPlayer()
            pending?.invoke()
            pending = null
        }
        if (current == null) current = store.lastQueue().let { (list, i) -> list.getOrNull(i) }
        loadPopular()
        loadWorldwide()
    }

    override fun onCleared() {
        store.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
    }

    private fun say(text: String) {
        messageChannel.trySend(text)
    }

    private fun syncFromPlayer() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val station = item?.station() ?: item?.mediaId?.let(store::find)
        if (station != null) current = station
        isPlaying = c.isPlaying
        playWhenReady = c.playWhenReady
        isLoading = c.playWhenReady && c.playbackState == Player.STATE_BUFFERING
        error = c.playerError?.let(::describe)
        queueSize = c.mediaItemCount
        val title = c.mediaMetadata.title?.toString()?.trim()
        nowPlaying = title?.takeIf { it.isNotEmpty() && station != null && it != station.name && it != "-" }
    }

    private fun describe(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> "Can't reach the station. Check your internet connection."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        -> "This station seems to be off the air right now."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        -> "This station's stream isn't in a format the phone can play."
        else -> "Couldn't play this station."
    }

    /** Runs [action] with the player, waiting for it to connect if need be. */
    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else pending = { controller?.let(action) }
    }

    // ----- Playback -----

    /** Plays [station], with [list] (the list it was picked from) as what next/previous step through. */
    fun play(station: Station, list: List<Station>) {
        if (current?.id == station.id && controller?.currentMediaItem?.mediaId == station.id) {
            if (!playWhenReady || error != null) resume()
            showPlayer = true
            return
        }
        current = station
        error = null
        nowPlaying = null
        val queue = list.ifEmpty { listOf(station) }.let { l -> if (l.any { it.id == station.id }) l else listOf(station) + l }
        val index = queue.indexOfFirst { it.id == station.id }.coerceAtLeast(0)
        withController { c ->
            c.setMediaItems(queue.map { it.toMediaItem() }, index, C.TIME_UNSET)
            c.prepare()
            c.play()
        }
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            current?.let { play(it, store.lastQueue().first) }
            return
        }
        if (c.playWhenReady && c.playerError == null) c.pause() else resume()
    }

    private fun resume() = withController { c ->
        if (c.mediaItemCount == 0) {
            current?.let { st -> play(st, store.lastQueue().first) }
            return@withController
        }
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun next() {
        val c = controller ?: return
        if (c.mediaItemCount < 2) return
        c.seekToNextMediaItem()
        c.play()
    }

    fun previous() {
        val c = controller ?: return
        if (c.mediaItemCount < 2) return
        c.seekToPreviousMediaItem()
        c.play()
    }

    fun setSleep(minutes: Int) {
        withController { c ->
            c.sendCustomCommand(SessionCommand(PlaybackService.CMD_SLEEP, Bundle.EMPTY), Bundle().apply { putInt("minutes", minutes) })
        }
        say(if (minutes > 0) "The radio will stop in $minutes minutes." else "Sleep timer off.")
    }

    // ----- Favourites -----

    fun isFavorite(station: Station): Boolean = favorites.any { it.id == station.id }

    fun toggleFavorite(station: Station) {
        val add = !isFavorite(station)
        store.setFavorite(station, add)
        say(if (add) "Added “${station.name}” to Favourites." else "Removed “${station.name}” from Favourites.")
    }

    fun moveFavorite(from: Int, to: Int) = store.moveFavorite(from, to)

    fun clearRecents() = store.clearRecents()

    /** Adds a station from a stream link the user typed in, to Favourites. Returns an error to show, or null. */
    fun addCustomStation(name: String, link: String, onDone: (String?) -> Unit) {
        val url = link.trim()
        val host = runCatching { URL(url).host }.getOrNull()
        if (!(url.startsWith("http://", true) || url.startsWith("https://", true)) || host.isNullOrEmpty()) {
            onDone("Enter a link starting with http:// or https://")
            return
        }
        viewModelScope.launch {
            val stream = withContext(Dispatchers.IO) { StreamLinks.resolve(url) }
            val station = Station(
                id = Station.CUSTOM_PREFIX + UUID.randomUUID(),
                name = name.trim().ifEmpty { host.removePrefix("www.") },
                url = stream,
            )
            store.setFavorite(station, true)
            say("Added “${station.name}” to Favourites.")
            onDone(null)
        }
    }

    // ----- The directory -----

    fun setCountry(code: String) {
        store.countryCode = code
        countryCode = code
        showCountries = false
        loadPopular()
        if (genre != null && !genreWorldwide) loadGenre()
        if (searchInCountry) runSearch()
    }

    fun loadPopular(more: Boolean = false) {
        if (more && (popular.loading || !popular.canLoadMore)) return
        popularJob?.cancel()
        val before = if (more) popular.stations else emptyList()
        popular = popular.copy(stations = before, loading = true, error = null)
        val code = countryCode
        popularJob = viewModelScope.launch {
            popular = fetch(before) { RadioBrowser.popular(code, offset = before.size, limit = PAGE) }
        }
    }

    private fun loadWorldwide() {
        worldwide = worldwide.copy(loading = true, error = null)
        viewModelScope.launch {
            worldwide = fetch(emptyList()) { RadioBrowser.popular("", limit = 25) }.copy(canLoadMore = false)
        }
    }

    fun retryHome() {
        loadPopular()
        if (worldwide.stations.isEmpty()) loadWorldwide()
    }

    fun openGenre(g: Genre) {
        genre = g
        genreWorldwide = countryCode.isEmpty()
        genreList = StationList()
        loadGenre()
    }

    fun closeGenre() {
        genre = null
        genreJob?.cancel()
    }

    fun setGenreWorldwide(worldwide: Boolean) {
        if (genreWorldwide == worldwide) return
        genreWorldwide = worldwide
        loadGenre()
    }

    fun loadGenre(more: Boolean = false) {
        val g = genre ?: return
        if (more && (genreList.loading || !genreList.canLoadMore)) return
        genreJob?.cancel()
        val before = if (more) genreList.stations else emptyList()
        genreList = genreList.copy(stations = before, loading = true, error = null)
        val code = if (genreWorldwide) "" else countryCode
        genreJob = viewModelScope.launch {
            genreList = fetch(before) { RadioBrowser.byTag(g.tag, code, offset = before.size, limit = PAGE) }
        }
    }

    fun loadCountries() {
        if (countries.isNotEmpty()) return
        countriesError = false
        viewModelScope.launch {
            try {
                countries = RadioBrowser.countries()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                countriesError = true
            }
        }
    }

    fun updateQuery(text: String) {
        query = text
        searchJob?.cancel()
        if (text.trim().length < 2) {
            results = StationList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            search(text.trim())
        }
    }

    fun setSearchInCountry(inCountry: Boolean) {
        searchInCountry = inCountry
        runSearch()
    }

    private fun runSearch() {
        val q = query.trim()
        if (q.length < 2) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { search(q) }
    }

    private suspend fun search(q: String) {
        results = results.copy(loading = true, error = null)
        val code = if (searchInCountry) countryCode else ""
        results = fetch(emptyList()) { RadioBrowser.search(q, code) }.copy(canLoadMore = false)
    }

    /** Fetches a page of stations and adds it to [before]; a failure keeps what was there. */
    private suspend fun fetch(before: List<Station>, page: suspend () -> List<Station>): StationList = try {
        val got = page()
        val seen = before.mapTo(HashSet()) { it.name.lowercase() }
        StationList(before + got.filter { seen.add(it.name.lowercase()) }, canLoadMore = got.size >= PAGE)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        StationList(before, error = "Couldn't load stations. Check your internet connection.", canLoadMore = before.isNotEmpty())
    }

    companion object {
        private const val PAGE = 40
    }
}

/** Turns a playlist link (.pls, .m3u) into the stream inside it; other links are returned as they are. */
object StreamLinks {
    fun resolve(link: String): String {
        val path = link.substringBefore('?').lowercase()
        if (!(path.endsWith(".pls") || path.endsWith(".m3u"))) return link
        return runCatching {
            val conn = URL(link).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("User-Agent", "Vaanoli/1.0 (Android)")
                val text = conn.inputStream.bufferedReader().use { r ->
                    val buf = CharArray(64 * 1024)
                    val n = r.read(buf)
                    if (n > 0) String(buf, 0, n) else ""
                }
                // .pls: "File1=http://…"; .m3u: the link on a line of its own.
                text.lineSequence()
                    .map { it.trim() }
                    .map { line -> if (line.startsWith("File", true) && '=' in line) line.substringAfter('=').trim() else line }
                    .firstOrNull { it.startsWith("http://", true) || it.startsWith("https://", true) }
            } finally {
                conn.disconnect()
            }
        }.getOrNull() ?: link
    }
}
