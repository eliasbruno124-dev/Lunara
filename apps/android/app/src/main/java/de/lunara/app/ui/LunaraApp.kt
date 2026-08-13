package de.lunara.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.lunara.app.ui.main.MainShell
import de.lunara.app.ui.onboarding.OnboardingRoute
import de.lunara.app.ui.theme.LunaraTheme

@Composable
fun LunaraApp(viewModel: RootViewModel = hiltViewModel()) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val loggingOut by viewModel.loggingOut.collectAsStateWithLifecycle()
    val dark = preferences?.let { if (it.useSystemTheme) isSystemInDarkTheme() else it.darkMode } ?: isSystemInDarkTheme()

    LunaraTheme(darkTheme = dark) {
        Surface(Modifier.fillMaxSize()) {
            when {
                preferences == null || loggingOut -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                preferences?.session == null -> OnboardingRoute()
                else -> MainShell(
                    session = requireNotNull(preferences?.session),
                    preferences = requireNotNull(preferences),
                    onLogout = viewModel::logout,
                )
            }
        }
    }
}
