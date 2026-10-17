package io.github.akrishna87.radio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.akrishna87.radio.GENRES
import io.github.akrishna87.radio.Genre
import io.github.akrishna87.radio.RadioViewModel
import io.github.akrishna87.radio.Station
import java.util.Calendar

/** Adds a card per station to a list; picking one plays it with [list] as the queue. */
private fun LazyListScope.stationRows(vm: RadioViewModel, list: List<Station>, keyPrefix: String, ranked: Boolean = false) {
    itemsIndexed(list, key = { _, s -> keyPrefix + s.id }) { i, station ->
        StationRow(
            station = station,
            isCurrent = vm.current?.id == station.id,
            isPlaying = vm.isPlaying,
            favorite = vm.isFavorite(station),
            onClick = { vm.play(station, list) },
            onFavorite = { vm.toggleFavorite(station) },
            rank = if (ranked) i + 1 else null,
        )
    }
}

private fun countryLabel(code: String): String =
    if (code.isEmpty()) "🌍 Worldwide" else listOf(Station.flag(code), Station.countryName(code).ifEmpty { code }).filter { it.isNotEmpty() }.joinToString(" ")

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning ☀️"
    in 12..16 -> "Good afternoon ✌️"
    in 17..21 -> "Good evening ✨"
    else -> "Late-night vibes 🌙"
}

/** A big bold page title. */
@Composable
private fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.headlineLarge,
        color = Brand.Text,
    )
}

// ----- Home -----

@Composable
fun HomeScreen(vm: RadioViewModel) {
    val countryName = Station.countryName(vm.countryCode).ifEmpty { vm.countryCode }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = Brand.SubText)
                    Text("Vaanalai", style = WordmarkStyle)
                    Text("வானலை · sky waves", style = MaterialTheme.typography.bodySmall, color = Brand.Faint)
                }
                GlassPill(
                    countryLabel(vm.countryCode),
                    onClick = { vm.showCountries = true },
                    trailing = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Change country", tint = Brand.SubText, modifier = Modifier.size(18.dp)) },
                )
            }
        }

        val playing = vm.current
        if (playing != null) {
            item { NowPlayingCard(vm, playing) }
        }

        if (vm.recents.isNotEmpty()) {
            item {
                SectionHeader("Jump back in") {
                    TextButton(onClick = vm::clearRecents) { Text("Clear", color = Brand.SubText) }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(vm.recents, key = { it.id }) { station ->
                        StationTile(station, vm.current?.id == station.id, vm.isPlaying, onClick = { vm.play(station, vm.recents) })
                    }
                }
            }
        }

        item { SectionHeader("Pick a vibe ✦") }
        item {
            LazyHorizontalGrid(
                rows = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                gridItemsIndexed(GENRES, key = { _, g -> g.tag }) { i, g ->
                    GenreTile(g.emoji, g.label, Brand.TilePalettes[i % Brand.TilePalettes.size], onClick = { vm.openGenre(g) })
                }
            }
        }

        item { SectionHeader(if (vm.countryCode.isEmpty()) "🔥 Popular stations" else "🔥 Popular in $countryName") }
        stationRows(vm, vm.popular.stations, "p:", ranked = true)
        item { ListFooter(vm.popular, onMore = { vm.loadPopular(more = true) }, onRetry = vm::retryHome) }

        if (vm.countryCode.isNotEmpty() && vm.worldwide.stations.isNotEmpty()) {
            item { SectionHeader("🌍 Trending worldwide") }
            stationRows(vm, vm.worldwide.stations, "w:", ranked = true)
        }
    }
}

/** A glowing card for the station that's on, at the top of Home. */
@Composable
private fun NowPlayingCard(vm: RadioViewModel, station: Station) {
    val (c1, c2) = stationColors(station.name)
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(c1.copy(alpha = 0.55f), Brand.Violet.copy(alpha = 0.35f), c2.copy(alpha = 0.25f))))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), shape)
            .clickable { vm.showPlayer = true }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NowPlayingArt(station, vm.songArt, 64.dp, corner = 18.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.isPlaying) {
                    Pill("ON AIR")
                } else {
                    Pill("TUNE IN", background = Color.White.copy(alpha = 0.18f), color = Color.White)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(station.name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                vm.nowPlaying?.let { "♪ $it" } ?: station.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        GradientPlayButton(vm, 52.dp)
    }
}

// ----- A genre or language -----

@Composable
fun GenreScreen(vm: RadioViewModel, genre: Genre) {
    val index = GENRES.indexOfFirst { it.tag == genre.tag }.coerceAtLeast(0)
    val palette = Brand.TilePalettes[index % Brand.TilePalettes.size]
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(palette.first.copy(alpha = 0.55f), palette.second.copy(alpha = 0.2f), Color.Transparent)))
                .padding(bottom = 10.dp),
        ) {
            Column {
                IconButton(onClick = vm::closeGenre, modifier = Modifier.padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(genre.emoji, fontSize = 34.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(genre.label, style = MaterialTheme.typography.headlineLarge, color = Color.White)
                }
                if (vm.countryCode.isNotEmpty()) {
                    Row(Modifier.padding(start = 20.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassPill(countryLabel(vm.countryCode), onClick = { vm.showGenreWorldwide(false) }, selected = !vm.genreWorldwide)
                        GlassPill("🌍 Worldwide", onClick = { vm.showGenreWorldwide(true) }, selected = vm.genreWorldwide)
                    }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            stationRows(vm, vm.genreList.stations, "g:")
            item {
                ListFooter(
                    vm.genreList,
                    onMore = { vm.loadGenre(more = true) },
                    onRetry = { vm.loadGenre() },
                    empty = if (vm.genreWorldwide) "No ${genre.label} stations found." else "No ${genre.label} stations here. Try Worldwide.",
                )
            }
        }
    }
}

// ----- Search -----

private val QUICK_SEARCHES = listOf("Mirchi", "BBC", "Lo-fi", "Chennai", "Ilaiyaraaja", "News", "Jazz", "AIR")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(vm: RadioViewModel) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { if (vm.query.isEmpty()) runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize()) {
        PageTitle("Search")
        TextField(
            value = vm.query,
            onValueChange = vm::updateQuery,
            placeholder = { Text("Station, city, language or genre", color = Brand.Faint) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Brand.SubText) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.updateQuery("") }) { Icon(Icons.Rounded.Clear, contentDescription = "Clear", tint = Brand.SubText) }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Brand.Surface2,
                unfocusedContainerColor = Brand.Surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Brand.Pink,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp)
                .focusRequester(focus),
        )
        if (vm.countryCode.isNotEmpty()) {
            Row(Modifier.padding(start = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassPill("🌍 Everywhere", onClick = { vm.searchOnlyInCountry(false) }, selected = !vm.searchInCountry)
                GlassPill("Only ${countryLabel(vm.countryCode)}", onClick = { vm.searchOnlyInCountry(true) }, selected = vm.searchInCountry)
            }
        }
        if (vm.query.trim().length < 2) {
            SectionHeader("Try these")
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QUICK_SEARCHES.forEach { q -> GlassPill(q, onClick = { vm.updateQuery(q) }) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                stationRows(vm, vm.results.stations, "s:")
                item {
                    ListFooter(vm.results, onMore = {}, onRetry = { vm.updateQuery(vm.query) }, empty = "No stations match “${vm.query.trim()}”.")
                }
            }
        }
    }
}

// ----- Favourites -----

@Composable
fun FavoritesScreen(vm: RadioViewModel) {
    val favorites = vm.favorites
    // Android's photo picker: no permission needed, and the app only sees the one picture chosen.
    // Saved by id, so it survives Android recreating the screen while the picker is open.
    var pickingFor by rememberSaveable { mutableStateOf<String?>(null) }
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val station = vm.favorites.firstOrNull { it.id == pickingFor }
        pickingFor = null
        if (uri != null && station != null) vm.setStationImage(station, uri)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Favourites", style = MaterialTheme.typography.headlineLarge, color = Brand.Text, modifier = Modifier.weight(1f))
                GradientButton(
                    "Add a station",
                    onClick = { vm.showAddStation = true },
                    leading = { Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        if (favorites.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💜", fontSize = 56.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No favourites yet", style = MaterialTheme.typography.titleLarge, color = Brand.Text)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Tap ♡ on a station to keep it here, or add one by its stream link.",
                        color = Brand.SubText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }
        gridItemsIndexed(favorites, key = { _, s -> s.id }) { index, station ->
            Box {
                var menu by remember { mutableStateOf(false) }
                StationTile(
                    station,
                    isCurrent = vm.current?.id == station.id,
                    isPlaying = vm.isPlaying,
                    onClick = { vm.play(station, favorites) },
                    modifier = Modifier.fillMaxWidth(),
                    size = 160.dp,
                )
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                    IconButton(
                        onClick = { menu = true },
                        modifier = Modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                    ) { Icon(Icons.Rounded.MoreHoriz, contentDescription = "More", tint = Color.White) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (index > 0) {
                            DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; vm.moveFavorite(index, index - 1) })
                        }
                        if (index < favorites.size - 1) {
                            DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; vm.moveFavorite(index, index + 1) })
                        }
                        DropdownMenuItem(
                            text = { Text("Choose picture") },
                            onClick = {
                                menu = false
                                pickingFor = station.id
                                pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        )
                        if (station.image.isNotEmpty()) {
                            DropdownMenuItem(text = { Text("Remove picture") }, onClick = { menu = false; vm.clearStationImage(station) })
                        }
                        DropdownMenuItem(text = { Text("Remove from Favourites") }, onClick = { menu = false; vm.toggleFavorite(station) })
                    }
                }
            }
        }
    }
}
