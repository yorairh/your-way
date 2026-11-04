package com.yourway.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yourway.app.R
import com.yourway.app.data.AppTheme
import com.yourway.app.data.LanguageSetting
import com.yourway.app.data.SavedKind
import com.yourway.app.data.SavedPlace

fun savedIcon(place: SavedPlace): ImageVector = when (place.kind) {
    SavedKind.HOME -> Icons.Default.Home
    SavedKind.WORK -> Icons.Default.Work
    SavedKind.FAVORITE -> Icons.Default.Star
    SavedKind.RECENT -> Icons.Default.History
}

@Composable
fun savedTitle(place: SavedPlace): String = when (place.kind) {
    SavedKind.HOME -> "${stringResource(R.string.home)}: ${place.name}"
    SavedKind.WORK -> "${stringResource(R.string.work)}: ${place.name}"
    else -> place.name
}

@Composable
fun SavedScreen(vm: AppViewModel) {
    val saved by vm.saved.collectAsState()
    val home = saved.firstOrNull { it.kind == SavedKind.HOME }
    val work = saved.firstOrNull { it.kind == SavedKind.WORK }
    AppScaffold(stringResource(R.string.saved_places), onBack = vm::back) { modifier ->
        LazyColumn(modifier.fillMaxSize()) {
            item { FixedPlace(R.string.home, Icons.Default.Home, home, vm) { vm.startPicking(PickFor.HOME) } }
            item { FixedPlace(R.string.work, Icons.Default.Work, work, vm) { vm.startPicking(PickFor.WORK) } }
            item { SavedHeading(R.string.favorites) }
            items(saved.filter { it.kind == SavedKind.FAVORITE }) { SavedRow(it, vm) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.add_favorite)) },
                    leadingContent = { Icon(Icons.Default.Add, null) },
                    modifier = Modifier.clickable { vm.startPicking(PickFor.FAVORITE) },
                )
            }
            val recents = saved.filter { it.kind == SavedKind.RECENT }
            if (recents.isNotEmpty()) item { SavedHeading(R.string.recent) }
            items(recents) { SavedRow(it, vm) }
        }
    }
}

@Composable
private fun FixedPlace(label: Int, icon: ImageVector, place: SavedPlace?, vm: AppViewModel, onClick: () -> Unit) {
    ListItem(
        overlineContent = { Text(stringResource(label)) },
        headlineContent = { Text(place?.name ?: stringResource(R.string.not_set)) },
        leadingContent = { Icon(icon, null) },
        trailingContent = {
            if (place != null) {
                IconButton(onClick = { vm.deleteSaved(place.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SavedRow(place: SavedPlace, vm: AppViewModel) {
    ListItem(
        headlineContent = { Text(place.name) },
        supportingContent = { if (place.subtitle.isNotEmpty()) Text(place.subtitle) },
        leadingContent = { Icon(savedIcon(place), null) },
        trailingContent = {
            IconButton(onClick = { vm.deleteSaved(place.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
        },
    )
}

@Composable
private fun SavedHeading(label: Int) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val current by vm.maxWalkMeters.collectAsState()
    var value by remember(current) { mutableFloatStateOf(current.toFloat()) }
    val context = LocalContext.current
    val chosen = LanguageSetting.get(context)
    AppScaffold(stringResource(R.string.settings), onBack = vm::back) { modifier ->
        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageChoice(chosen, LanguageSetting.SYSTEM, stringResource(R.string.language_system), context)
                LanguageChoice(chosen, LanguageSetting.ENGLISH, stringResource(R.string.language_english), context)
                LanguageChoice(chosen, LanguageSetting.HEBREW, stringResource(R.string.language_hebrew), context)
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.theme_colour), style = MaterialTheme.typography.titleSmall)
            val theme by vm.theme.collectAsState()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChoice(theme, AppTheme.BLUE, R.string.theme_blue, vm)
                ThemeChoice(theme, AppTheme.GREEN, R.string.theme_green, vm)
                ThemeChoice(theme, AppTheme.ORANGE, R.string.theme_orange, vm)
                ThemeChoice(theme, AppTheme.PURPLE, R.string.theme_purple, vm)
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.max_walk), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.meters, value.toInt()), style = MaterialTheme.typography.titleMedium)
            Slider(
                value = value,
                onValueChange = { value = it },
                onValueChangeFinished = { vm.setMaxWalk(value.toInt()) },
                valueRange = 200f..2000f,
                steps = 8,
            )
        }
    }
}

@Composable
private fun ThemeChoice(chosen: AppTheme, theme: AppTheme, label: Int, vm: AppViewModel) {
    FilterChip(
        selected = chosen == theme,
        onClick = { vm.setTheme(theme) },
        label = { Text(stringResource(label)) },
        leadingIcon = { Box(Modifier.size(16.dp).background(themeSeed(theme), CircleShape)) },
    )
}

@Composable
private fun LanguageChoice(chosen: String, choice: String, label: String, context: Context) {
    FilterChip(
        selected = chosen == choice,
        onClick = {
            if (chosen != choice) {
                LanguageSetting.set(context, choice)
                // Restarting the screen is what makes Android reload every string and flip the layout direction.
                context.findActivity()?.recreate()
            }
        },
        label = { Text(label) },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
