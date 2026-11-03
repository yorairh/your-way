package com.yourway.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yourway.app.R
import com.yourway.app.data.Place

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@Composable
fun PickerScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val saved by vm.saved.collectAsState()
    val picker = state.picker
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.any { it }) vm.useCurrentLocation() else vm.onLocationDenied()
    }

    val title = when (state.pickFor) {
        PickFor.START -> R.string.choose_start
        PickFor.DESTINATION -> R.string.choose_destination
        PickFor.HOME -> R.string.home
        PickFor.WORK -> R.string.work
        PickFor.FAVORITE -> R.string.add_favorite
    }
    val forTrip = state.pickFor == PickFor.START || state.pickFor == PickFor.DESTINATION

    AppScaffold(stringResource(title), onBack = vm::back) { modifier ->
        Column(modifier.fillMaxSize().imePadding()) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    vm.onQueryChange(it)
                },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("place-search"),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                if (forTrip) {
                    item {
                        ListItem(
                            headlineContent = { Text(stringResource(if (picker.locating) R.string.locating else R.string.my_location)) },
                            leadingContent = { Icon(Icons.Default.MyLocation, null) },
                            modifier = Modifier.clickable {
                                val granted = LOCATION_PERMISSIONS.any {
                                    context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                                }
                                if (granted) vm.useCurrentLocation() else askPermission.launch(LOCATION_PERMISSIONS)
                            },
                        )
                    }
                }
                picker.message?.let { message -> item { Notice(stringResource(message), error = true) } }

                if (text.isBlank()) {
                    if (forTrip) {
                        items(saved) { place ->
                            ListItem(
                                headlineContent = { Text(savedTitle(place)) },
                                supportingContent = { if (place.subtitle.isNotEmpty()) Text(place.subtitle) },
                                leadingContent = { Icon(savedIcon(place), null) },
                                modifier = Modifier.clickable { vm.choose(place.toPlace()) },
                            )
                        }
                    }
                } else {
                    if (picker.stops.isNotEmpty()) item { SectionTitle(stringResource(R.string.stops)) }
                    items(picker.stops) { place -> ResultRow(place, Icons.Default.DirectionsBus, vm) }
                    if (picker.stopsFailed) item { Notice(stringResource(R.string.stops_unavailable), error = true) }

                    if (picker.places.isNotEmpty()) item { SectionTitle(stringResource(R.string.places)) }
                    items(picker.places) { place -> ResultRow(place, Icons.Default.Place, vm) }
                    if (picker.placesFailed) item { Notice(stringResource(R.string.places_unavailable), error = false) }

                    if (picker.searchingStops || picker.searchingPlaces) {
                        item { Notice(stringResource(R.string.searching), error = false) }
                    } else if (picker.stops.isEmpty() && picker.places.isEmpty() && !picker.stopsFailed && !picker.placesFailed) {
                        item { Notice(stringResource(R.string.no_results), error = false) }
                    }
                    if (picker.places.isNotEmpty()) item { Notice(stringResource(R.string.osm_credit), error = false) }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(place: Place, icon: ImageVector, vm: AppViewModel) {
    ListItem(
        headlineContent = { Text(place.name) },
        supportingContent = { if (place.subtitle.isNotEmpty()) Text(place.subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = {
            IconButton(onClick = { vm.saveFavorite(place) }) {
                Icon(Icons.Default.StarBorder, stringResource(R.string.save_favorite))
            }
        },
        modifier = Modifier.clickable { vm.choose(place) },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Notice(text: String, error: Boolean) {
    Text(
        text,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(16.dp),
    )
}
