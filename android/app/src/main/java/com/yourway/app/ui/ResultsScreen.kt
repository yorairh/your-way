package com.yourway.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yourway.app.R
import com.yourway.app.data.Itinerary
import com.yourway.app.data.TransitLeg
import com.yourway.app.data.TransitMode
import com.yourway.app.data.WalkLeg
import java.time.format.DateTimeFormatter

val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun TransitMode.icon(): ImageVector = when (this) {
    TransitMode.BUS -> Icons.Default.DirectionsBus
    TransitMode.TRAIN -> Icons.Default.Train
    TransitMode.LIGHT_RAIL -> Icons.Default.Tram
}

@Composable
fun ResultsScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    AppScaffold(stringResource(R.string.results_title), onBack = vm::back) { modifier ->
        Box(modifier.fillMaxSize()) {
            when (val results = state.results) {
                ResultsState.Loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.finding_routes))
                }
                ResultsState.Empty -> Message(R.string.no_routes)
                ResultsState.Expired -> Message(R.string.schedule_expired)
                ResultsState.Failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.routes_failed))
                    TextButton(onClick = vm::findRoutes) { Text(stringResource(R.string.retry)) }
                }
                is ResultsState.Found -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(results.trips) { trip -> TripCard(trip) { vm.select(trip) } }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.Message(text: Int) {
    Text(stringResource(text), Modifier.align(Alignment.Center).padding(24.dp))
}

@Composable
private fun TripCard(trip: Itinerary, onClick: () -> Unit) {
    val transfers = when (trip.transfers) {
        0 -> stringResource(R.string.direct)
        1 -> stringResource(R.string.one_transfer)
        else -> stringResource(R.string.transfers, trip.transfers)
    }
    val source = stringResource(if (trip.hasLiveData) R.string.live_times else R.string.from_schedule)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${trip.departure.format(clockFormat)} – ${trip.arrival.format(clockFormat)}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.minutes, trip.duration.toMinutes().toInt()), style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                trip.legs.forEach { leg ->
                    when (leg) {
                        is WalkLeg -> Icon(Icons.Default.DirectionsWalk, null)
                        is TransitLeg -> AssistChip(onClick = onClick, label = { Text(leg.line) }, leadingIcon = { Icon(leg.mode.icon(), null) })
                    }
                }
            }
            Text(
                "$transfers · ${stringResource(R.string.walking_meters, trip.walkMeters)} · $source",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
