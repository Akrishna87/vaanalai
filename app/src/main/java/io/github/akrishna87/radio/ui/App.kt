package io.github.akrishna87.radio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.akrishna87.radio.RadioViewModel
import io.github.akrishna87.radio.Tab

@Composable
fun RadioApp(vm: RadioViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it, withDismissAction = true) }
    }

    BackHandler(enabled = vm.showPlayer) { vm.showPlayer = false }
    BackHandler(enabled = !vm.showPlayer && vm.genre != null) { vm.closeGenre() }
    BackHandler(enabled = !vm.showPlayer && vm.genre == null && vm.tab != Tab.HOME) { vm.tab = Tab.HOME }

    Box(Modifier.fillMaxSize().background(Brand.Background)) {
        Scaffold(
            containerColor = Brand.Background,
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(data, containerColor = Brand.Surface2, contentColor = Brand.Text, actionColor = Brand.Lime, dismissActionContentColor = Brand.SubText, shape = RoundedCornerShape(18.dp))
                }
            },
            bottomBar = {
                Column(Modifier.background(Brush.verticalGradient(listOf(Color.Transparent, Brand.Background.copy(alpha = 0.92f), Brand.Background)))) {
                    if (vm.current != null) MiniPlayer(vm)
                    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                        Tab.entries.forEach { tab ->
                            val selected = vm.tab == tab && vm.genre == null
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    vm.closeGenre()
                                    vm.tab = tab
                                },
                                icon = {
                                    Icon(
                                        when (tab) {
                                            Tab.HOME -> if (selected) Icons.Rounded.Home else Icons.Outlined.Home
                                            Tab.SEARCH -> if (selected) Icons.Rounded.Search else Icons.Outlined.Search
                                            Tab.FAVORITES -> if (selected) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder
                                        },
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(tab.label, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.Black,
                                    selectedTextColor = Brand.Text,
                                    indicatorColor = Brand.Lime,
                                    unselectedIconColor = Brand.SubText,
                                    unselectedTextColor = Brand.SubText,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                val genre = vm.genre
                when {
                    genre != null -> GenreScreen(vm, genre)
                    vm.tab == Tab.HOME -> HomeScreen(vm)
                    vm.tab == Tab.SEARCH -> SearchScreen(vm)
                    else -> FavoritesScreen(vm)
                }
            }
        }

        AnimatedVisibility(
            visible = vm.showPlayer && vm.current != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            PlayerScreen(vm)
        }
    }

    if (vm.showCountries) CountryPicker(vm)
    if (vm.showAddStation) AddStationDialog(vm)
    if (vm.showSleep) SleepDialog(vm)
}

/** What's playing, floating above the tabs; tap to open the full player. */
@Composable
private fun MiniPlayer(vm: RadioViewModel) {
    val station = vm.current ?: return
    val (c1, _) = stationColors(station.name)
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(16.dp, shape, ambientColor = c1, spotColor = c1)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(c1.copy(alpha = 0.35f), Brand.Surface2, Brand.Surface2)))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), shape)
            .clickable { vm.showPlayer = true }
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationLogo(station, 46.dp, corner = 14.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(station.name, style = MaterialTheme.typography.titleSmall, color = Brand.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.isPlaying) {
                    PlayingBars(height = 10.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    statusText(vm),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (vm.error != null) MaterialTheme.colorScheme.error else Brand.SubText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        GradientPlayButton(vm, 46.dp)
    }
}

/** The round gradient play/pause button; shows a spinner while connecting. */
@Composable
fun GradientPlayButton(vm: RadioViewModel, size: Dp, modifier: Modifier = Modifier) {
    val playing = vm.playWhenReady && vm.error == null
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brand.Gradient)
            .clickable(onClick = vm::togglePlay),
        contentAlignment = Alignment.Center,
    ) {
        if (vm.isLoading) {
            CircularProgressIndicator(Modifier.size(size * 0.62f), strokeWidth = 2.5.dp, color = Color.White)
        }
        Icon(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (playing) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier.size(if (vm.isLoading) size * 0.3f else size * 0.55f),
        )
    }
}

fun statusText(vm: RadioViewModel): String = when {
    vm.error != null -> vm.error!!
    vm.isLoading -> "Connecting…"
    vm.isPlaying -> vm.nowPlaying?.let { "♪ $it" } ?: "Live"
    vm.playWhenReady -> "Connecting…"
    else -> "Paused"
}

// ----- Dialogs -----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPicker(vm: RadioViewModel) {
    LaunchedEffect(Unit) { vm.loadCountries() }
    var filter by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = { vm.showCountries = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Brand.Surface,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Text(
                "Stations from",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Find a country") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = CircleShape,
            )
            val shown = vm.countries.filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                if (filter.isBlank()) {
                    item {
                        CountryRow("🌍 Worldwide", null, vm.countryCode.isEmpty()) { vm.setCountry("") }
                    }
                }
                items(shown, key = { it.code }) { c ->
                    CountryRow(c.label, "${c.stations} stations", vm.countryCode == c.code) { vm.setCountry(c.code) }
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        when {
                            vm.countriesError -> TextButton(onClick = vm::loadCountries) { Text("Couldn't load countries. Try again") }
                            vm.countries.isEmpty() -> CircularProgressIndicator(Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryRow(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) Brand.Lime else Brand.Text,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
        )
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddStationDialog(vm: RadioViewModel) {
    var name by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val shownProblem = problem
    val problemText: (@Composable () -> Unit)? = if (shownProblem != null) {
        { Text(shownProblem) }
    } else {
        null
    }
    AlertDialog(
        onDismissRequest = { if (!saving) vm.showAddStation = false },
        containerColor = Brand.Surface2,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Add a station", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(
                    "Paste the link to a station's stream (it often ends in .mp3, .aac, .m3u8, .pls or .m3u).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Station name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it; problem = null },
                    label = { Text("Stream link") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    isError = problem != null,
                    supportingText = problemText,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && link.isNotBlank(),
                onClick = {
                    saving = true
                    vm.addCustomStation(name, link) { err ->
                        saving = false
                        if (err == null) vm.showAddStation = false else problem = err
                    }
                },
            ) { Text(if (saving) "Adding…" else "Save") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = { vm.showAddStation = false }) { Text("Cancel") }
        },
    )
}

@Composable
private fun SleepDialog(vm: RadioViewModel) {
    val active = vm.sleepUntil > System.currentTimeMillis()
    AlertDialog(
        onDismissRequest = { vm.showSleep = false },
        containerColor = Brand.Surface2,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Sleep timer 🌙", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(
                    "Stop the radio after:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                listOf(15, 30, 45, 60, 90, 120).forEach { m ->
                    Text(
                        if (m < 60) "$m minutes" else if (m == 60) "1 hour" else "${m / 60.0} hours".replace(".0 ", " "),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                vm.setSleep(m)
                                vm.showSleep = false
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = {
            if (active) {
                TextButton(onClick = { vm.setSleep(0); vm.showSleep = false }) { Text("Turn off") }
            }
        },
        dismissButton = { TextButton(onClick = { vm.showSleep = false }) { Text("Close") } },
    )
}
