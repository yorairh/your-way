package com.yourway.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yourway.app.R
import com.yourway.app.data.TransitLeg
import com.yourway.app.data.WalkLeg

@Composable
fun DetailScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val trip = state.selected ?: return
    AppScaffold(stringResource(R.string.results_title), onBack = vm::back) { modifier ->
        Column(modifier.fillMaxSize()) {
            RouteMap(trip, Modifier.fillMaxWidth().weight(1f))
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(trip.legs) { leg ->
                    when (leg) {
                        is WalkLeg -> ListItem(
                            overlineContent = { Text(leg.start.format(clockFormat)) },
                            headlineContent = { Text(stringResource(R.string.walk_to, leg.meters, leg.toName)) },
                            leadingContent = { Icon(Icons.Default.DirectionsWalk, null) },
                        )
                        is TransitLeg -> ListItem(
                            overlineContent = { Text("${leg.start.format(clockFormat)} – ${leg.end.format(clockFormat)}") },
                            headlineContent = { Text(stringResource(R.string.ride_line, leg.line, leg.headsign)) },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.ride_detail, leg.fromStop, leg.toStop, leg.stopCount) + " · " +
                                        when {
                                            !leg.isLive -> stringResource(R.string.from_schedule)
                                            leg.delayMinutes > 0 -> stringResource(R.string.live_late, leg.delayMinutes)
                                            else -> stringResource(R.string.live_on_time)
                                        },
                                )
                            },
                            leadingContent = { Icon(leg.mode.icon(), null) },
                        )
                    }
                    HorizontalDivider()
                }
                if (trip.missedTransfer) {
                    item {
                        Text(
                            stringResource(R.string.missed_transfer),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                item {
                    Text(
                        stringResource(R.string.walking_approx),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
