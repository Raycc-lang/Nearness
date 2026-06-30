package com.raycc.nearness

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import com.raycc.nearness.data.AuthRepository
import com.raycc.nearness.ui.AppState
import com.raycc.nearness.ui.MainScreen
import com.raycc.nearness.ui.NearnessTheme
import com.raycc.nearness.ui.RootViewModel
import com.raycc.nearness.ui.auth.AuthScreen
import com.raycc.nearness.ui.auth.AuthViewModel
import com.raycc.nearness.ui.pairing.PairingScreen

class MainActivity : ComponentActivity() {

    private val authRepository = AuthRepository()
    private val authViewModel by viewModels<AuthViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLink(intent)
        setContent {
            NearnessTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NearnessApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val data = intent.data
        Log.d("Nearness", "handleDeepLink data=$data fragment=${data?.fragment}")
        if (data == null) return
        val error = authRepository.handleDeeplink(intent)
        if (error != null) authViewModel.onDeepLinkError(error)
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
