package io.github.akrishna87.radio

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

/**
 * Owns the player. Media3 turns the session into the media notification, lock-screen controls
 * and headphone/car/Bluetooth button handling, and keeps the radio going in the background.
 *
 * The queue is the list a station was picked from, so "next" and "previous" (on the screen,
 * the notification, headphones or in the car) change station.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    companion object {
        /** Custom command: sleep timer. Arg "minutes": > 0 to stop after that long, 0 to cancel. */
        const val CMD_SLEEP = "io.github.akrishna87.radio.SLEEP"

        private const val ROOT = "root"
        private const val FOLDER_FAVORITES = "folder:favorites"
        private const val FOLDER_RECENT = "folder:recent"
        private const val FOLDER_POPULAR = "folder:popular"

        /** Tries at reconnecting a station that dropped, before giving up and showing an error. */
        private const val MAX_RETRIES = 4
        /** Resuming after a pause longer than this jumps back to live instead of playing old audio. */
        private const val STALE_AFTER_MS = 30_000L
        private const val SLEEP_FADE_MS = 20_000L
    }

    private var session: MediaLibrarySession? = null
    private lateinit var exo: ExoPlayer
    private lateinit var player: RadioPlayer
    private val store by lazy { StationStore(this) }
    private val scope = MainScope()

    private var retries = 0
    /** Times in a row the station hung up soon after starting. */
    private var hangUps = 0
    /** The station being reconnected to after it hung up, or C.INDEX_UNSET. */
    private var returningTo = C.INDEX_UNSET
    private var retryJob: Job? = null
    private var playingSince = 0L
    private var pausedAt = 0L
    private var sleepJob: Job? = null
    /** Stations from the directory shown in the car, so a pick there can be played. */
    private var carPopular: List<Station> = emptyList()

    /** The player as the outside world sees it: previous/next always change station. */
    private inner class RadioPlayer(player: ExoPlayer) : ForwardingPlayer(player) {
        override fun seekToNext() = seekToNextMediaItem()
        override fun seekToPrevious() = seekToPreviousMediaItem()

        override fun play() {
            // A stream paused for a while has old audio waiting in its buffer; start again at live.
            if (pausedAt > 0 && SystemClock.elapsedRealtime() - pausedAt > STALE_AFTER_MS &&
                playbackState != Player.STATE_IDLE
            ) {
                reconnect()
            }
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                retries = 0 // asked to try again after giving up
                prepare()
            }
            pausedAt = 0
            super.play()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Vaanoli/1.0 (Android)")
            .setAllowCrossProtocolRedirects(true) // many stations redirect between http and https
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK) // keep Wi-Fi awake while streaming with the screen off
            .build()
        // A station that ends (the server hung up) wraps round instead of stopping; see onMediaItemTransition.
        exo.repeatMode = Player.REPEAT_MODE_ALL
        player = RadioPlayer(exo)
        setSleep(0)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, player, SessionCallback())
            .setSessionActivity(openApp)
            .build()

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) playingSince = SystemClock.elapsedRealtime()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                pausedAt = if (playWhenReady) 0 else SystemClock.elapsedRealtime()
                if (!playWhenReady) retryJob?.cancel()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    // A live stream "ended": the station hung up. Go back to it and reconnect,
                    // rather than moving on to the next station by itself; give up if it keeps
                    // hanging up straight away.
                    val quick = playingSince == 0L || SystemClock.elapsedRealtime() - playingSince < 15_000
                    hangUps = if (quick) hangUps + 1 else 1
                    if (hangUps > MAX_RETRIES) {
                        exo.pause()
                        hangUps = 0
                    }
                    val count = exo.mediaItemCount
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && count > 1) {
                        returningTo = (exo.currentMediaItemIndex - 1 + count) % count
                        exo.seekToDefaultPosition(returningTo)
                    }
                    return
                }
                if (exo.currentMediaItemIndex == returningTo) {
                    returningTo = C.INDEX_UNSET
                    return
                }
                returningTo = C.INDEX_UNSET
                hangUps = 0
                retries = 0
                playingSince = 0
                retryJob?.cancel()
                val station = mediaItem?.station() ?: return
                store.addRecent(station)
                saveQueue()
                scope.launch { RadioBrowser.countPlay(station.id) }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Dropped connection or a station having a moment: try again a few times, waiting
                // a little longer each time. A station that played for a while gets fresh tries.
                if (playingSince > 0 && SystemClock.elapsedRealtime() - playingSince > 60_000) retries = 0
                if (!exo.playWhenReady || retries >= MAX_RETRIES) return
                retries++
                retryJob?.cancel()
                retryJob = scope.launch {
                    delay(2_000L * retries)
                    if (exo.playWhenReady && exo.playbackState == Player.STATE_IDLE) exo.prepare()
                }
            }
        })
    }

    private fun reconnect() {
        exo.stop()
        exo.prepare()
    }

    private fun saveQueue() {
        val stations = (0 until exo.mediaItemCount).mapNotNull { exo.getMediaItemAt(it).station() }
        if (stations.isNotEmpty()) store.saveQueue(stations, exo.currentMediaItemIndex.coerceIn(0, stations.size - 1))
    }

    /** Stops the radio after [minutes], fading out over the last few seconds; 0 cancels. */
    private fun setSleep(minutes: Int) {
        sleepJob?.cancel()
        exo.volume = 1f
        if (minutes <= 0) {
            store.prefs.edit().putLong(StationStore.KEY_SLEEP_UNTIL, 0L).apply()
            return
        }
        val until = System.currentTimeMillis() + minutes * 60_000L
        store.prefs.edit().putLong(StationStore.KEY_SLEEP_UNTIL, until).apply()
        sleepJob = scope.launch {
            delay((until - System.currentTimeMillis() - SLEEP_FADE_MS).coerceAtLeast(0))
            while (true) {
                val left = until - System.currentTimeMillis()
                if (left <= 0) break
                exo.volume = (left.toFloat() / SLEEP_FADE_MS).coerceIn(0f, 1f)
                delay(200)
            }
            exo.pause()
            exo.volume = 1f
            store.prefs.edit().putLong(StationStore.KEY_SLEEP_UNTIL, 0L).apply()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away keeps the radio going if it's playing; otherwise shut down.
        if (!exo.playWhenReady || exo.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveQueue()
        store.prefs.edit().putLong(StationStore.KEY_SLEEP_UNTIL, 0L).apply()
        scope.cancel()
        session?.release()
        session = null
        exo.release()
        super.onDestroy()
    }

    // ----- Session callbacks -----

    private inner class SessionCallback : MediaLibrarySession.Callback {

        /** The app's own screens and its notification; the uid comes from Android, so it can't be faked. */
        private fun isOwnApp(controller: MediaSession.ControllerInfo) = controller.uid == Process.myUid()

        private fun isCar(session: MediaSession, controller: MediaSession.ControllerInfo) =
            session.isAutoCompanionController(controller) || session.isAutomotiveController(controller)

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            if (isOwnApp(controller)) {
                val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(SessionCommand(CMD_SLEEP, Bundle.EMPTY))
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(commands)
                    .build()
            }
            if (isCar(session, controller)) {
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS)
                    .build()
            }
            // Headphones, watches and the lock screen: play, pause and change station. The phone's
            // own media controls may also bring back the last station; other apps can't pick what plays.
            val player = if (controller.isTrusted) {
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            } else {
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .removeAll(Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_CHANGE_MEDIA_ITEMS)
                    .build()
            }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS)
                .setAvailablePlayerCommands(player)
                .build()
        }

        private fun mayBrowse(session: MediaSession, controller: MediaSession.ControllerInfo) =
            isOwnApp(controller) || isCar(session, controller)

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            if (!mayBrowse(session, browser)) Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_PERMISSION_DENIED))
            else Futures.immediateFuture(LibraryResult.ofItem(folderItem(ROOT, getString(R.string.app_name)), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (!mayBrowse(session, browser)) {
                return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_PERMISSION_DENIED))
            }
            return scope.future<LibraryResult<ImmutableList<MediaItem>>> {
                val all: List<MediaItem> = when (parentId) {
                    ROOT -> listOf(
                        folderItem(FOLDER_FAVORITES, "Favourites"),
                        folderItem(FOLDER_RECENT, "Recently played"),
                        folderItem(FOLDER_POPULAR, popularTitle()),
                    )
                    FOLDER_FAVORITES -> store.favorites().map { it.toMediaItem() }
                    FOLDER_RECENT -> store.recents().map { it.toMediaItem() }
                    FOLDER_POPULAR -> popular().map { it.toMediaItem() }
                    else -> return@future LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                }
                val from = (page.coerceAtLeast(0).toLong() * pageSize).coerceAtMost(all.size.toLong()).toInt()
                val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(all.size)
                LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
            }
        }

        private fun popularTitle(): String {
            val name = Station.countryName(store.countryCode)
            return if (name.isEmpty()) "Popular stations" else "Popular in $name"
        }

        private suspend fun popular(): List<Station> {
            if (carPopular.isEmpty()) {
                carPopular = runCatching { RadioBrowser.popular(store.countryCode, limit = 50) }.getOrDefault(emptyList())
            }
            return carPopular
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (!mayBrowse(session, browser)) {
                return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_PERMISSION_DENIED))
            }
            val item = known(mediaId)?.toMediaItem()
            return Futures.immediateFuture(
                if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE),
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (!isOwnApp(controller)) {
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_PERMISSION_DENIED))
            }
            if (customCommand.customAction == CMD_SLEEP) {
                setSleep(args.getInt("minutes"))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        /** A station the service already knows by id. */
        private fun known(id: String): Station? = store.find(id) ?: carPopular.firstOrNull { it.id == id }

        /**
         * Rebuilds a requested item from its station. Only the app itself may hand over a new station
         * (with its stream link); everyone else can only pick stations the app already knows.
         */
        private fun resolve(controller: MediaSession.ControllerInfo, item: MediaItem): MediaItem? {
            val sent = if (isOwnApp(controller)) Station.fromJson(item.requestMetadata.extras?.getString(EXTRA_STATION)) else null
            return (sent ?: known(item.mediaId))?.toMediaItem()
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val items = mediaItems.mapNotNull { resolve(controller, it) }
            if (items.isEmpty() && mediaItems.isNotEmpty()) {
                return Futures.immediateFailedFuture(UnsupportedOperationException("Unknown station"))
            }
            return Futures.immediateFuture(items.toMutableList())
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val single = mediaItems.singleOrNull()
            if (single != null && isCar(mediaSession, controller)) {
                // A station picked in the car plays with the rest of its list; "Hey Google, play
                // <station> on Vaanoli" searches the directory.
                val query = single.requestMetadata.searchQuery
                return scope.future<MediaSession.MediaItemsWithStartPosition> {
                    val (list, at) = if (!query.isNullOrBlank()) {
                        RadioBrowser.search(query, "") to 0
                    } else {
                        listOf(store.favorites(), store.recents(), carPopular)
                            .firstOrNull { l -> l.any { it.id == single.mediaId } }
                            ?.let { l -> l to l.indexOfFirst { it.id == single.mediaId } }
                            ?: (emptyList<Station>() to 0)
                    }
                    if (list.isEmpty()) throw UnsupportedOperationException("No station to play")
                    MediaSession.MediaItemsWithStartPosition(list.map { it.toMediaItem() }, at, C.TIME_UNSET)
                }
            }
            val items = mutableListOf<MediaItem>()
            var start = 0
            mediaItems.forEachIndexed { i, item ->
                val resolved = resolve(controller, item) ?: return@forEachIndexed
                if (i == startIndex) start = items.size
                items += resolved
            }
            if (items.isEmpty()) return Futures.immediateFailedFuture(UnsupportedOperationException("Unknown station"))
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, start, C.TIME_UNSET))
        }

        /** A headphone/car "play" press after the app was closed brings back the last station. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val (list, index) = store.lastQueue()
            if (list.isEmpty()) return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(list.map { it.toMediaItem() }, index, C.TIME_UNSET))
        }
    }
}
