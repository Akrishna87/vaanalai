package io.github.akrishna87.radio.ui

import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.akrishna87.radio.Logos
import io.github.akrishna87.radio.RadioViewModel
import io.github.akrishna87.radio.Station
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Where friends can get the app; used when sharing a station. */
private const val APP_LINK = "https://github.com/Akrishna87/vaanalai/releases/download/radio-latest/Vaanalai.apk"

/** The full player: glowing logo, what's on, a visualiser and the controls, tinted with the station's colours. */
@Composable
fun PlayerScreen(vm: RadioViewModel) {
    val station = vm.current ?: return
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vm.sleepUntil) {
        while (vm.sleepUntil > 0) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val sleepLeftMin = ((vm.sleepUntil - now + 59_999) / 60_000).takeIf { vm.sleepUntil > now }

    // The station's colour: from its logo when it has a colourful one, otherwise from its name.
    val logo = rememberLogo(station, 280.dp)
    val (nameColor, nameColor2) = stationColors(station.name)
    val accent = remember(logo) { logo?.let(Logos::accentColor)?.let { Color(it) } } ?: nameColor
    val accent2 = if (accent == nameColor) nameColor2 else Brand.Violet

    Box(Modifier.fillMaxSize().background(Brand.Background)) {
        GlowBackground(accent, accent2)
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.KeyboardArrowDown, "Close player", onClick = { vm.showPlayer = false })
                // Tapping the badge jumps to what's on air right now.
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val badge = Modifier.minimumInteractiveComponentSize().clip(CircleShape).clickable(onClickLabel = "Go to live", onClick = vm::goLive)
                    if (vm.isPlaying) {
                        Pill("● LIVE", modifier = badge)
                    } else {
                        Pill(if (vm.isLoading) "TUNING IN" else "PAUSED · GO LIVE", modifier = badge, background = Color.White.copy(alpha = 0.14f), color = Color.White)
                    }
                }
                GlassIconButton(
                    Icons.Rounded.Bedtime,
                    "Sleep timer",
                    onClick = { vm.showSleep = true },
                    tint = if (sleepLeftMin != null) Brand.Lime else Color.White,
                )
            }

            Spacer(Modifier.height(30.dp))
            StationLogo(
                station,
                272.dp,
                corner = 44.dp,
                modifier = Modifier.shadow(48.dp, RoundedCornerShape(44.dp), ambientColor = accent, spotColor = accent),
            )
            Spacer(Modifier.height(30.dp))

            Text(
                station.name,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = station.subtitle
            if (sub.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(sub, color = Color.White.copy(alpha = 0.65f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(18.dp))
            Visualiser(playing = vm.isPlaying, colors = listOf(Brand.Pink, accent, Brand.Cyan))
            Spacer(Modifier.height(14.dp))

            // What's on now (the song, or the connection's state) in a glass pill.
            Text(
                statusText(vm),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = when {
                    vm.error != null -> MaterialTheme.colorScheme.error
                    vm.nowPlaying != null && vm.isPlaying -> Brand.Lime
                    else -> Color.White.copy(alpha = 0.85f)
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (vm.error != null) {
                Spacer(Modifier.height(6.dp))
                Text("Tap play to try again.", color = Brand.SubText, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                val canSkip = vm.queueSize > 1
                GlassIconButton(Icons.Rounded.SkipPrevious, "Previous station", onClick = vm::previous, size = 60.dp, enabled = canSkip)
                GradientPlayButton(
                    vm,
                    86.dp,
                    Modifier.shadow(28.dp, CircleShape, ambientColor = Brand.Pink, spotColor = Brand.Pink),
                )
                GlassIconButton(Icons.Rounded.SkipNext, "Next station", onClick = vm::next, size = 60.dp, enabled = canSkip)
            }

            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                val fav = vm.isFavorite(station)
                GlassIconButton(
                    if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (fav) "Remove from Favourites" else "Add to Favourites",
                    onClick = { vm.toggleFavorite(station) },
                    tint = if (fav) Brand.Pink else Color.White,
                )
                GlassIconButton(Icons.Rounded.Share, "Share station", onClick = { shareStation(context, station) })
                if (station.homepage.startsWith("http")) {
                    GlassIconButton(Icons.Rounded.Language, "Station website", onClick = { runCatching { uriHandler.openUri(station.homepage) } })
                }
            }
            if (sleepLeftMin != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    if (sleepLeftMin <= 1) "🌙 Stops in under a minute" else "🌙 Stops in $sleepLeftMin min",
                    color = Brand.Lime,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { vm.showSleep = true }.padding(8.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

private fun shareStation(context: android.content.Context, station: Station) {
    val text = buildString {
        append("🎧 Listening to ${station.name} on Vaanalai")
        if (station.homepage.startsWith("http")) append("\n${station.homepage}")
        append("\n\nGet the app (Android): $APP_LINK")
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, "Share ${station.name}")) }
}

/** A round translucent button with a hairline border. */
@Composable
private fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    size: Dp = 52.dp,
    tint: Color = Color.White,
    enabled: Boolean = true,
) {
    Box(
        Modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Two big soft blobs of the station's colours, drifting slowly behind everything. */
@Composable
private fun GlowBackground(c1: Color, c2: Color) {
    val t = rememberInfiniteTransition(label = "glow")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing)),
        label = "phase",
    )
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val a = Offset(w * (0.3f + 0.15f * cos(phase)), h * (0.22f + 0.06f * sin(phase)))
        val b = Offset(w * (0.75f + 0.12f * sin(phase)), h * (0.5f + 0.08f * cos(phase)))
        drawCircle(Brush.radialGradient(listOf(c1.copy(alpha = 0.55f), Color.Transparent), center = a, radius = w * 0.9f), radius = w * 0.9f, center = a)
        drawCircle(Brush.radialGradient(listOf(c2.copy(alpha = 0.40f), Color.Transparent), center = b, radius = w * 0.8f), radius = w * 0.8f, center = b)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Brand.Background.copy(alpha = 0.85f)), startY = h * 0.45f, endY = h))
    }
}

/** A row of bars that dance while the radio plays (a visual flourish, not a real analyser). */
@Composable
private fun Visualiser(playing: Boolean, colors: List<Color>, bars: Int = 28) {
    val t = rememberInfiniteTransition(label = "vis")
    val time by t.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Restart),
        label = "time",
    )
    val brush = Brush.horizontalGradient(colors)
    Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        val gap = 4.dp.toPx()
        val barW = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val level = if (playing) {
                val wave = sin(time * (1 + i % 3) + i * 0.7f) * 0.5f + 0.5f
                val wave2 = sin(time * 2 + i * 1.9f) * 0.5f + 0.5f
                0.18f + 0.82f * (wave * 0.65f + wave2 * 0.35f)
            } else {
                0.12f
            }
            val barH = size.height * level
            drawRoundRect(
                brush = brush,
                topLeft = Offset(i * (barW + gap), (size.height - barH) / 2),
                size = androidx.compose.ui.geometry.Size(barW, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2, barW / 2),
            )
        }
    }
}
