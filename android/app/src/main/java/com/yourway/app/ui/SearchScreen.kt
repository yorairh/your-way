package com.yourway.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yourway.app.R
import com.yourway.app.data.Place
import com.yourway.app.data.PlaceKind
import com.yourway.app.data.TimeMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun SearchScreen(vm: AppViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    AppScaffold(
        title = stringResource(R.string.app_name),
        actions = {
            IconButton(onClick = { vm.open(Screen.SAVED) }) { Icon(Icons.Default.Star, stringResource(R.string.saved_places)) }
            IconButton(onClick = { vm.open(Screen.SETTINGS) }) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
        },
    ) { modifier ->
        Column(
            modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaceField(stringResource(R.string.from), s.start, stringResource(R.string.choose_start)) { vm.startPicking(PickFor.START) }
                    PlaceField(stringResource(R.string.to), s.destination, stringResource(R.string.choose_destination)) { vm.startPicking(PickFor.DESTINATION) }
                }
                IconButton(onClick = vm::swap) { Icon(Icons.Default.SwapVert, stringResource(R.string.swap)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeChip(s.timeMode == TimeMode.NOW, R.string.leave_now) { vm.setTimeMode(TimeMode.NOW) }
                TimeChip(s.timeMode == TimeMode.LEAVE_AT, R.string.leave_at) { vm.setTimeMode(TimeMode.LEAVE_AT) }
                TimeChip(s.timeMode == TimeMode.ARRIVE_BY, R.string.arrive_by) { vm.setTimeMode(TimeMode.ARRIVE_BY) }
            }
            if (s.timeMode != TimeMode.NOW) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate(context, s.time, vm::setTime) }) { Text(s.time.format(dateFormat)) }
                    OutlinedButton(onClick = { pickTime(context, s.time, vm::setTime) }) { Text(s.time.format(timeFormat)) }
                }
            }
            Button(
                onClick = vm::findRoutes,
                enabled = s.start != null && s.destination != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.find_routes)) }
        }
    }
}

@Composable
private fun PlaceField(label: String, place: Place?, empty: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(place?.let { placeTitle(it) } ?: empty, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun placeTitle(place: Place): String =
    if (place.kind == PlaceKind.CURRENT_LOCATION) stringResource(R.string.my_location) else place.name

@Composable
private fun TimeChip(selected: Boolean, label: Int, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(label)) })
}

private fun pickDate(context: Context, current: LocalDateTime, onPicked: (LocalDateTime) -> Unit) {
    DatePickerDialog(context, { _, year, month, day ->
        onPicked(current.withDayOfMonth(1).withYear(year).withMonth(month + 1).withDayOfMonth(day))
    }, current.year, current.monthValue - 1, current.dayOfMonth).show()
}

private fun pickTime(context: Context, current: LocalDateTime, onPicked: (LocalDateTime) -> Unit) {
    TimePickerDialog(context, { _, hour, minute ->
        onPicked(current.withHour(hour).withMinute(minute))
    }, current.hour, current.minute, true).show()
}
