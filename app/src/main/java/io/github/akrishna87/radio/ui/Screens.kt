package io.github.akrishna87.radio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akrishna87.radio.GENRES
import io.github.akrishna87.radio.Genre
import io.github.akrishna87.radio.RadioViewModel
import io.github.akrishna87.radio.Station

/** Adds a row per station to a list; picking one plays it with [list] as the queue. */
private fun LazyListScope.stationRows(vm: RadioViewModel, list: List<Station>, keyPrefix: String) {
    items(list, key = { keyPrefix + it.id }) { station ->
        StationRow(
            station = station,
            isCurrent = vm.current?.id == station.id,
            isPlaying = vm.isPlaying,
            favorite = vm.isFavorite(station),
            onClick = { vm.play(station, list) },
            onFavorite = { vm.toggleFavorite(station) },
        )
    }
}

private fun countryLabel(code: String): String =
    if (code.isEmpty()) "🌍 Worldwide" else listOf(Station.flag(code), Station.countryName(code).ifEmpty { code }).filter { it.isNotEmpty() }.joinToString(" ")

// ----- Home -----

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: RadioViewModel) {
    val countryName = Station.countryName(vm.countryCode).ifEmpty { vm.countryCode }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Vaanoli", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("வானொலி · radio from everywhere", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(
                    onClick = { vm.showCountries = true },
                    label = { Text(countryLabel(vm.countryCode)) },
                    trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                )
            }
        }

        if (vm.recents.isNotEmpty()) {
            item {
                SectionHeader("Recently played") {
                    TextButton(onClick = vm::clearRecents) { Text("Clear") }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(vm.recents, key = { it.id }) { station ->
                        StationTile(station, vm.current?.id == station.id) { vm.play(station, vm.recents) }
                    }
                }
            }
        }

        item { SectionHeader("Browse") }
        item {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GENRES.forEach { g ->
                    SuggestionChip(onClick = { vm.openGenre(g) }, label = { Text(g.label) })
                }
            }
        }

        item { SectionHeader(if (vm.countryCode.isEmpty()) "Popular stations" else "Popular in $countryName") }
        stationRows(vm, vm.popular.stations, "p:")
        item { ListFooter(vm.popular, onMore = { vm.loadPopular(more = true) }, onRetry = vm::retryHome) }

        if (vm.countryCode.isNotEmpty() && vm.worldwide.stations.isNotEmpty()) {
            item { SectionHeader("Popular worldwide") }
            stationRows(vm, vm.worldwide.stations, "w:")
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ----- A genre or language -----

@Composable
fun GenreScreen(vm: RadioViewModel, genre: Genre) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeGenre) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text(genre.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        if (vm.countryCode.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !vm.genreWorldwide,
                    onClick = { vm.setGenreWorldwide(false) },
                    label = { Text(countryLabel(vm.countryCode)) },
                )
                FilterChip(
                    selected = vm.genreWorldwide,
                    onClick = { vm.setGenreWorldwide(true) },
                    label = { Text("🌍 Worldwide") },
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
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

@Composable
fun SearchScreen(vm: RadioViewModel) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { if (vm.query.isEmpty()) runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = vm::updateQuery,
            placeholder = { Text("Station, city, language or genre") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.updateQuery("") }) { Icon(Icons.Rounded.Clear, contentDescription = "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                .focusRequester(focus),
        )
        if (vm.countryCode.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !vm.searchInCountry,
                    onClick = { vm.setSearchInCountry(false) },
                    label = { Text("🌍 Everywhere") },
                )
                FilterChip(
                    selected = vm.searchInCountry,
                    onClick = { vm.setSearchInCountry(true) },
                    label = { Text("Only ${countryLabel(vm.countryCode)}") },
                )
            }
        }
        if (vm.query.trim().length < 2) {
            Text(
                "Try “BBC”, “Mirchi”, “jazz”, “Chennai” or “news”.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
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
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Favourites", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.showAddStation = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add a station")
                }
            }
        }
        if (favorites.isEmpty()) {
            item {
                Text(
                    "No favourites yet.\nTap ♡ next to a station to keep it here, or add one by its stream link.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        }
        itemsIndexed(favorites, key = { _, s -> s.id }) { index, station ->
            Box {
                var menu by remember { mutableStateOf(false) }
                StationRow(
                    station = station,
                    isCurrent = vm.current?.id == station.id,
                    isPlaying = vm.isPlaying,
                    favorite = true,
                    onClick = { vm.play(station, favorites) },
                    onFavorite = { menu = true },
                    trailing = {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (index > 0) {
                                DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; vm.moveFavorite(index, index - 1) })
                            }
                            if (index < favorites.size - 1) {
                                DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; vm.moveFavorite(index, index + 1) })
                            }
                            DropdownMenuItem(text = { Text("Remove from Favourites") }, onClick = { menu = false; vm.toggleFavorite(station) })
                        }
                    },
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
