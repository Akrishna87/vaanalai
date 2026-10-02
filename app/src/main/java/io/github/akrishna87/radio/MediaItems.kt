package io.github.akrishna87.radio

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes

/** Where a media item keeps its whole station, as JSON. */
const val EXTRA_STATION = "io.github.akrishna87.radio.STATION"

/** What the notification and lock screen show under the station's name. */
fun Station.description(): String =
    listOfNotNull(
        countryLabel().ifEmpty { null },
        tags.take(2).joinToString(", ").ifEmpty { null },
    ).joinToString(" · ").ifEmpty { "Live radio" }

fun Station.toMediaItem(): MediaItem {
    val json = toJson().toString()
    val extras = Bundle().apply { putString(EXTRA_STATION, json) }
    val art = favicon.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let(Uri::parse)
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(url)
        .apply { if (hls || url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8) }
        // Items lose their stream link on the way from the screens to the player, so the station
        // travels along in the request and the player rebuilds the item from it.
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setArtist(description())
                .setArtworkUri(art)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                .setExtras(Bundle(extras))
                .build(),
        )
        .build()
}

/** The station a media item plays, if it carries one. */
fun MediaItem.station(): Station? =
    Station.fromJson(mediaMetadata.extras?.getString(EXTRA_STATION))
        ?: Station.fromJson(requestMetadata.extras?.getString(EXTRA_STATION))

/** A folder of stations for Android Auto. */
fun folderItem(id: String, title: String): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setIsPlayable(false)
            .setIsBrowsable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
            .build(),
    )
    .build()
