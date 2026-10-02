package io.github.akrishna87.radio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.radio.RadioViewModel
import kotlinx.coroutines.delay

/** The full player: big logo, what's on, and the controls. */
@Composable
fun PlayerScreen(vm: RadioViewModel) {
    val station = vm.current ?: return
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vm.sleepUntil) {
        while (vm.sleepUntil > 0) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val sleepLeftMin = ((vm.sleepUntil - now + 59_999) / 60_000).takeIf { vm.sleepUntil > now }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.background, colors.background))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.showPlayer = false }) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close player", modifier = Modifier.size(32.dp))
                }
                Text(
                    if (vm.isPlaying) "● LIVE" else "LIVE RADIO",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (vm.isPlaying) colors.primary else colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { vm.showSleep = true }) {
                    Icon(
                        Icons.Rounded.Bedtime,
                        contentDescription = "Sleep timer",
                        tint = if (sleepLeftMin != null) colors.primary else colors.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            StationLogo(
                station,
                260.dp,
                corner = 28.dp,
                modifier = Modifier.shadow(24.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
            )
            Spacer(Modifier.height(28.dp))

            Text(
                station.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = station.subtitle
            if (sub.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(sub, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                statusText(vm),
                color = when {
                    vm.error != null -> colors.error
                    vm.nowPlaying != null && vm.isPlaying -> colors.primary
                    else -> colors.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(min = 48.dp),
            )
            if (vm.error != null) {
                Text("Tap play to try again.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                val canSkip = vm.queueSize > 1
                IconButton(onClick = vm::previous, enabled = canSkip, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous station", modifier = Modifier.size(36.dp))
                }
                PlayPauseButton(vm, Modifier.size(80.dp).shadow(12.dp, CircleShape), big = true)
                IconButton(onClick = vm::next, enabled = canSkip, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next station", modifier = Modifier.size(36.dp))
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FavoriteButton(vm.isFavorite(station), { vm.toggleFavorite(station) })
                if (station.homepage.startsWith("http")) {
                    TextButton(onClick = { runCatching { uriHandler.openUri(station.homepage) } }) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Website")
                    }
                }
                if (sleepLeftMin != null) {
                    TextButton(onClick = { vm.showSleep = true }) {
                        Text(if (sleepLeftMin <= 1) "Stops in under a minute" else "Stops in $sleepLeftMin min")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
