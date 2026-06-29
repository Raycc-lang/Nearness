package com.yourname.nearness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourname.nearness.ui.AppState
import com.yourname.nearness.ui.MainScreen
import com.yourname.nearness.ui.NearnessTheme
import com.yourname.nearness.ui.RootViewModel
import com.yourname.nearness.ui.auth.AuthScreen
import com.yourname.nearness.ui.pairing.PairingScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NearnessTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NearnessApp()
                }
            }
        }
    }
}

@Composable
private fun NearnessApp(rootViewModel: RootViewModel = viewModel()) {
    val state by rootViewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        AppState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }
        AppState.NeedsAuth -> AuthScreen()
        AppState.NeedsPairing -> PairingScreen(onPaired = rootViewModel::onPaired)
        is AppState.Ready -> MainScreen(userId = s.userId, partnerId = s.partnerId)
    }
}
