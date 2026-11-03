package com.yourway.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yourway.app.data.LanguageSetting
import com.yourway.app.ui.AppViewModel
import com.yourway.app.ui.CrashScreen
import com.yourway.app.ui.YourWayScreens

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageSetting.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
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
