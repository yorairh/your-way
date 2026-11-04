package com.yourway.app.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.yourway.app.R

@Composable
fun YourWayScreens(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    BackHandler(enabled = state.screen != Screen.SEARCH) { vm.back() }
    when (state.screen) {
        Screen.SEARCH -> SearchScreen(vm)
        Screen.PICKER -> PickerScreen(vm)
        Screen.SAVED -> SavedScreen(vm)
        Screen.SETTINGS -> SettingsScreen(vm)
        Screen.RESULTS -> ResultsScreen(vm)
        Screen.DETAIL -> DetailScreen(vm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            val dark = isSystemInDarkTheme()
            val colors = MaterialTheme.colorScheme
            val onBar = if (dark) colors.onPrimaryContainer else colors.onPrimary
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (dark) colors.primaryContainer else colors.primary,
                    titleContentColor = onBar,
                    navigationIconContentColor = onBar,
                    actionIconContentColor = onBar,
                ),
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
                actions = { actions() },
            )
        },
    ) { padding ->
        val tint = MaterialTheme.colorScheme.primary
        Box(Modifier.padding(padding).fillMaxSize().drawBehind { drawRails(tint) }) { content(Modifier) }
    }
}

private val railSeed = kotlin.random.Random.nextLong()

private class Track(val p0: Offset, val p1: Offset, val p2: Offset, val p3: Offset) {
    fun at(t: Float): Offset {
        val u = 1 - t
        return p0 * (u * u * u) + p1 * (3 * u * u * t) + p2 * (3 * u * t * t) + p3 * (t * t * t)
    }

    fun tangent(t: Float): Offset {
        val d = at((t + 0.01f).coerceAtMost(1f)) - at((t - 0.01f).coerceAtLeast(0f))
        val len = kotlin.math.sqrt(d.x * d.x + d.y * d.y).coerceAtLeast(0.001f)
        return Offset(d.x / len, d.y / len)
    }
}

/** Faint railway tracks (rails and sleepers) curving across the screen in random directions, with a train on one. */
private fun DrawScope.drawRails(tint: Color) {
    val rnd = kotlin.random.Random(railSeed)
    val w = size.width
    val h = size.height
    fun edge(): Offset = when (rnd.nextInt(4)) {
        0 -> Offset(rnd.nextFloat() * w, -40f)
        1 -> Offset(rnd.nextFloat() * w, h + 40f)
        2 -> Offset(-40f, rnd.nextFloat() * h)
        else -> Offset(w + 40f, rnd.nextFloat() * h)
    }
    fun inside() = Offset(w * (0.15f + rnd.nextFloat() * 0.7f), h * (0.15f + rnd.nextFloat() * 0.7f))
    val tracks = List(6) { Track(edge(), inside(), inside(), edge()) }
    val gauge = 9.dp.toPx()
    val samples = 160
    tracks.forEach { track ->
        val pts = List(samples + 1) { track.at(it / samples.toFloat()) }
        val norms = List(samples + 1) { val d = track.tangent(it / samples.toFloat()); Offset(-d.y, d.x) }
        for (side in listOf(-1f, 1f)) {
            for (i in 0 until samples) {
                drawLine(
                    tint.copy(alpha = 0.22f), pts[i] + norms[i] * (gauge * side), pts[i + 1] + norms[i + 1] * (gauge * side),
                    strokeWidth = 2.5.dp.toPx(),
                )
            }
        }
        var run = 0f
        for (i in 1..samples) {
            val d = pts[i] - pts[i - 1]
            run += kotlin.math.sqrt(d.x * d.x + d.y * d.y)
            if (run >= 14.dp.toPx()) {
                run = 0f
                drawLine(
                    tint.copy(alpha = 0.13f), pts[i] + norms[i] * (gauge + 4.dp.toPx()), pts[i] - norms[i] * (gauge + 4.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                )
            }
        }
    }
    // A little train (engine and two carriages) on the first track.
    for (trainIndex in 0..3) {
      val track = tracks[trainIndex]
      val front = 0.30f + rnd.nextFloat() * 0.5f
      for (car in 0..2) {
        val t = front - car * 0.055f
        val c = track.at(t)
        val d = track.tangent(t)
        val angle = Math.toDegrees(kotlin.math.atan2(d.y.toDouble(), d.x.toDouble())).toFloat()
        rotate(angle, pivot = c) {
            val len = 46.dp.toPx()
            val thick = 17.dp.toPx()
            val topLeft = Offset(c.x - len / 2, c.y - thick / 2)
            drawRoundRect(tint.copy(alpha = if (car == 0) 0.55f else 0.38f), topLeft, androidx.compose.ui.geometry.Size(len, thick), androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
            val window = tint.copy(alpha = 0.18f)
            for (k in 0..2) {
                drawRoundRect(window, Offset(topLeft.x + 6.dp.toPx() + k * 13.dp.toPx(), c.y - 4.dp.toPx()), androidx.compose.ui.geometry.Size(8.dp.toPx(), 8.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            }
        }
    }
    }
}

@Composable
fun CrashScreen(report: String, onContinue: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    AppScaffold(stringResource(R.string.crash_title)) { modifier ->
        Column(
            modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.crash_body))
            Button(onClick = { clipboard.setText(AnnotatedString(report)) }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.copy_report))
            }
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                    context.startActivity(Intent.createChooser(send, null))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.share_report)) }
            TextButton(onClick = onContinue, Modifier.fillMaxWidth()) { Text(stringResource(R.string.continue_to_app)) }
            Text(report, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** How trustworthy a time is: green on time, orange a little late, red very late, grey when it is only the timetable. */
@Composable
fun liveColor(isLive: Boolean, delayMinutes: Int): Color {
    val dark = isSystemInDarkTheme()
    return when {
        !isLive -> if (dark) Color(0xFFB0B0B8) else Color(0xFF6B6B73)
        delayMinutes <= 1 -> if (dark) Color(0xFF6FD176) else Color(0xFF1B7F2A)
        delayMinutes < 10 -> if (dark) Color(0xFFFFB454) else Color(0xFFB85C00)
        else -> if (dark) Color(0xFFFF7B72) else Color(0xFFC62828)
    }
}

/** A ride's status as coloured text: a dot and the words. */
@Composable
fun liveStatusText(isLive: Boolean, delayMinutes: Int): AnnotatedString {
    val text = when {
        !isLive -> stringResource(R.string.from_schedule)
        delayMinutes > 0 -> stringResource(R.string.live_late, delayMinutes)
        else -> stringResource(R.string.live_on_time)
    }
    return buildAnnotatedString {
        withStyle(SpanStyle(color = liveColor(isLive, delayMinutes), fontWeight = FontWeight.SemiBold)) { append("● $text") }
    }
}
