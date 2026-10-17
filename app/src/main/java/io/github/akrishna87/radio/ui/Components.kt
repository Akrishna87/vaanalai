package io.github.akrishna87.radio.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.akrishna87.radio.Logos
import io.github.akrishna87.radio.Station
import io.github.akrishna87.radio.StationList
import kotlin.math.absoluteValue

/** The station's logo bitmap, loading it if need be; null while loading or when it has none. */
@Composable
fun rememberLogo(station: Station, size: Dp): Bitmap? {
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtMost(512)
    var bitmap by remember(station.favicon) { mutableStateOf(Logos.cached(station.favicon)) }
    LaunchedEffect(station.favicon) {
        if (bitmap == null && station.favicon.isNotBlank()) bitmap = Logos.load(station.favicon, px)
    }
    return bitmap
}

/** Two colours picked from the station's name, for logo placeholders and the player's glow. */
fun stationColors(name: String): Pair<Color, Color> {
    val hue = (name.hashCode().absoluteValue % 360).toFloat()
    return Color.hsl(hue, 0.75f, 0.58f) to Color.hsl((hue + 50f) % 360f, 0.8f, 0.45f)
}

/** The station's logo on a rounded square, or its initials on a gradient when there's no logo. */
@Composable
fun StationLogo(station: Station, size: Dp, modifier: Modifier = Modifier, corner: Dp = size / 4) {
    val bitmap = rememberLogo(station, size)
    val shape = RoundedCornerShape(corner)
    val (c1, c2) = stationColors(station.name)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(if (bitmap != null) Brush.linearGradient(listOf(Color.White, Color.White)) else Brush.linearGradient(listOf(c1, c2))),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(size / 14),
            )
        } else {
            Text(
                initials(station.name),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.36f).sp,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
            )
        }
    }
}

private fun initials(name: String): String {
    val words = name.split(' ', '-', '_', '.').filter { it.isNotEmpty() && it[0].isLetterOrDigit() }
    return when {
        words.isEmpty() -> "♪"
        words.size == 1 -> words[0].take(2)
        else -> "${words[0][0]}${words[1][0]}"
    }.uppercase()
}

/** Little bars that bounce while a station plays. */
@Composable
fun PlayingBars(modifier: Modifier = Modifier, playing: Boolean = true, color: Color = Brand.Lime, bars: Int = 3, height: Dp = 14.dp) {
    val t = rememberInfiniteTransition(label = "bars")
    val speeds = listOf(500, 700, 420, 610, 380, 560, 470)
    val heights = (0 until bars).map { i ->
        t.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(speeds[i % speeds.size]), RepeatMode.Reverse),
            label = "bar",
        )
    }
    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        heights.forEach { h ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(if (playing) h.value else 0.25f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

@Composable
fun FavoriteButton(favorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (favorite) "Remove from Favourites" else "Add to Favourites",
            tint = if (favorite) Brand.Pink else Brand.SubText,
        )
    }
}

/** A small rounded label, e.g. "LIVE". */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, background: Color = Brand.Lime, color: Color = Color.Black) {
    Text(
        text,
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp,
        maxLines = 1,
    )
}

/** A filled pill button with the brand gradient. */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Brand.HorizontalGradient)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
    }
}

/** A pill with a translucent "glass" fill and a hairline border. */
@Composable
fun GlassPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(Brand.HorizontalGradient)
                else Modifier.background(Color.White.copy(alpha = 0.06f)).border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), CircleShape),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = if (selected) Color.White else Brand.Text,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
        trailing?.invoke()
    }
}

/**
 * One station: logo, name, where it's from and what it plays, and a heart, on a rounded card.
 * The station playing now glows with the brand gradient.
 */
@Composable
fun StationRow(
    station: Station,
    isCurrent: Boolean,
    isPlaying: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    /** Shown instead of the heart, e.g. a menu. */
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(shape)
            .background(if (isCurrent) Brand.Surface2 else Brand.Surface)
            .then(if (isCurrent) Modifier.border(BorderStroke(1.5.dp, Brand.Gradient), shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 2.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) {
            Text(
                "$rank",
                modifier = Modifier.width(30.dp),
                style = MaterialTheme.typography.titleLarge.copy(brush = Brand.Gradient),
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(6.dp))
        }
        StationLogo(station, 54.dp, corner = 16.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    PlayingBars(playing = isPlaying)
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    station.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Brand.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val sub = station.subtitle
            if (sub.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.SubText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) Box { trailing() } else FavoriteButton(favorite, onFavorite)
    }
}

/** A big square tile, for "Recently played" and Favourites. */
@Composable
fun StationTile(station: Station, isCurrent: Boolean, isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 128.dp) {
    Column(
        modifier
            .width(size)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        Box {
            StationLogo(
                station,
                size,
                corner = 28.dp,
                modifier = if (isCurrent) Modifier.border(BorderStroke(2.dp, Brand.Gradient), RoundedCornerShape(28.dp)) else Modifier,
            )
            if (isCurrent) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                ) { PlayingBars(playing = isPlaying, height = 12.dp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            station.name,
            style = MaterialTheme.typography.titleSmall,
            color = Brand.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** A colourful genre tile: emoji and name on a gradient. */
@Composable
fun GenreTile(emoji: String, label: String, palette: Pair<Color, Color>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 150.dp, height = 84.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(palette.first, palette.second)))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(emoji, fontSize = 26.sp, modifier = Modifier.align(Alignment.TopEnd))
        Text(
            label,
            modifier = Modifier.align(Alignment.BottomStart),
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 17.sp,
            letterSpacing = (-0.3).sp,
            maxLines = 1,
        )
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 26.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Brand.Text, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** What goes under a list: a spinner, an error with Retry, "Show more", or nothing. */
@Composable
fun ListFooter(list: StationList, onMore: () -> Unit, onRetry: () -> Unit, empty: String = "No stations found.") {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            list.loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = Brand.Pink)
            list.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(list.error, color = Brand.SubText, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                GlassPill("Try again", onClick = onRetry)
            }
            list.stations.isEmpty() -> Text(empty, color = Brand.SubText, textAlign = TextAlign.Center)
            list.canLoadMore -> GlassPill("Show more", onClick = onMore)
        }
    }
}
