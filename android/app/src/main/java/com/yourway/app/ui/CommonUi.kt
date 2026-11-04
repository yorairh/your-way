package com.yourway.app.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
    ) { padding -> content(Modifier.padding(padding)) }
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
