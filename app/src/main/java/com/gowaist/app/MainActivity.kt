package com.gowaist.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gowaist.app.data.settings.AppSettings
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.ui.AppNavigation
import com.gowaist.app.ui.CelebrationHost
import com.gowaist.app.ui.onboarding.OnboardingScreen
import com.gowaist.app.ui.theme.GoWaistTheme
import com.gowaist.app.ui.theme.isAppInDarkTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settings.settings.map<AppSettings, AppSettings?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            val dark = isAppInDarkTheme(s.themeMode)
            GoWaistTheme(dark = dark) {
                SystemBars(dark)
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (!s.onboardingDone) {
                        OnboardingScreen()
                    } else {
                        AppNavigation(s)
                    }
                    CelebrationHost()
                }
            }
        }
    }

    @Composable
    private fun SystemBars(dark: Boolean) {
        val scrim = MaterialTheme.colorScheme.background.copy(alpha = 0.0f).toArgb()
        LaunchedEffect(dark) {
            enableEdgeToEdge(
                statusBarStyle = if (dark) SystemBarStyle.dark(scrim) else SystemBarStyle.light(scrim, scrim),
                navigationBarStyle = if (dark) SystemBarStyle.dark(scrim) else SystemBarStyle.light(scrim, scrim),
            )
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
