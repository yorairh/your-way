package com.yourway.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yourway.app.data.LanguageSetting
import com.yourway.app.ui.AppViewModel
import com.yourway.app.ui.CrashScreen
import com.yourway.app.ui.YourWayScreens
import com.yourway.app.ui.yourWayColorScheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageSetting.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The title bar is always a dark colour, so the status bar icons are always light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            val theme by viewModel.theme.collectAsState()
            MaterialTheme(colorScheme = yourWayColorScheme(theme, isSystemInDarkTheme())) {
                var report by remember { mutableStateOf(CrashReporter.pending(this)) }
                val shown = report
                if (shown != null) {
                    CrashScreen(shown) {
                        CrashReporter.clear(this)
                        report = null
                    }
                } else {
                    YourWayScreens(viewModel)
                }
            }
        }
    }
}
