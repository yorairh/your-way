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
import androidx.compose.ui.text.buildAnnotatedString
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
                    items(results.trips) { trip -> TripCard(trip, onClick = { vm.select(trip) }, onPick = vm::select) }
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
private fun TripCard(trip: Itinerary, onClick: () -> Unit, onPick: (Itinerary) -> Unit) {
    val transfers = when (trip.transfers) {
        0 -> stringResource(R.string.direct)
        1 -> stringResource(R.string.one_transfer)
        else -> stringResource(R.string.transfers, trip.transfers)
    }
    val live = trip.rides.filter { it.isLive }
    val worst = live.maxOfOrNull { it.delayMinutes } ?: 0
    val source = liveStatusText(live.isNotEmpty(), worst)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val options = trip.alternatives
            if (options.size > 1) {
                val rides = trip.rides
                Text(
                    "${rides.first().fromStop} → ${rides.last().toStop}",
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${trip.departure.format(clockFormat)} – ${trip.arrival.format(clockFormat)}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.minutes, trip.duration.toMinutes().toInt()), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (options.size <= 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                trip.legs.forEach { leg ->
                    when (leg) {
                        is WalkLeg -> Icon(Icons.Default.DirectionsWalk, null)
                        is TransitLeg -> AssistChip(onClick = onClick, label = { Text(leg.line) }, leadingIcon = { Icon(leg.mode.icon(), null) })
                    }
                }
            }
            Text(
                buildAnnotatedString {
                    append("$transfers · ${stringResource(R.string.walking_meters, trip.walkMeters)} · ")
                    append(source)
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (options.size > 1) {
                options.forEach { option -> OptionRow(option, onClick = { onPick(option) }) }
            }
            if (trip.missedTransfer) {
                Text(stringResource(R.string.missed_transfer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** One bus (or combination of lines) within a route: its lines, destination, and when it leaves. */
@Composable
private fun OptionRow(option: Itinerary, onClick: () -> Unit) {
    val rides = option.rides
    val first = rides.firstOrNull() ?: return
    val live = rides.filter { it.isLive }
    val color = liveColor(live.isNotEmpty(), live.maxOfOrNull { it.delayMinutes } ?: 0)
    val minutes = java.time.Duration.between(java.time.LocalDateTime.now(), first.start).toMinutes().toInt().coerceAtLeast(0)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(first.mode.icon(), null, tint = color)
        Column(Modifier.weight(1f)) {
            Text(rides.joinToString(" › ") { it.line }, style = MaterialTheme.typography.titleMedium)
            Text(first.headsign, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.in_minutes, minutes), color = color, style = MaterialTheme.typography.titleMedium)
            Text(first.start.format(clockFormat), color = color, style = MaterialTheme.typography.bodySmall)
        }
    }
}
