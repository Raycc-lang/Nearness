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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.raycc.nearness.data.AuthRepository
import com.raycc.nearness.data.LocalCacheRepository
import com.raycc.nearness.ui.AppState
import com.raycc.nearness.ui.NearnessTheme
import com.raycc.nearness.ui.RootViewModel
import com.raycc.nearness.ui.Screen
import com.raycc.nearness.ui.archive.ArchiveScreen
import com.raycc.nearness.ui.auth.AuthScreen
import com.raycc.nearness.ui.auth.AuthViewModel
import com.raycc.nearness.ui.pairing.PairingScreen
import com.raycc.nearness.ui.today.TodayScreen
import com.raycc.nearness.ui.whiteboard.WhiteboardScreen

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
                    NearnessApp(authViewModel = authViewModel, authRepository = authRepository)
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
        // Status-only: the deep-link URI carries the one-time PKCE code.
        Log.d("Nearness", "handleDeepLink hasData=${data != null}")
        if (data == null) return
        val error = authRepository.handleDeeplink(intent)
        if (error != null) authViewModel.onDeepLinkError(error)
    }
}

@Composable
private fun NearnessApp(
    authViewModel: AuthViewModel,
    authRepository: AuthRepository,
    rootViewModel: RootViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                RootViewModel(cache = LocalCacheRepository(app))
            }
        },
    ),
) {
    val state by rootViewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    LaunchedEffect(state) {
        when (state) {
            AppState.NeedsAuth -> {
                navController.navigate(Screen.Auth) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is AppState.Ready -> {
                val currentRoute = navController.currentBackStackEntry?.destination?.route
                if (currentRoute == null || currentRoute.contains("Auth")) {
                    navController.navigate(Screen.Home) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            else -> {}
        }
    }

    when (val s = state) {
        AppState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> {
            NavHost(
                navController = navController,
                startDestination = if (s is AppState.NeedsAuth) Screen.Auth else Screen.Home,
            ) {
                composable<Screen.Auth> {
                    AuthScreen(viewModel = authViewModel)
                }
                composable<Screen.Home> {
                    val readyState = state as? AppState.Ready
                    if (readyState != null) {
                        TodayScreen(
                            userId = readyState.userId,
                            initialPartnerId = readyState.partnerId,
                            initialPartnershipId = readyState.partnershipId,
                            onPairClicked = { navController.navigate(Screen.Pairing) },
                            onWhiteboardClicked = { navController.navigate(Screen.Whiteboard) },
                            onPaired = { rootViewModel.onPaired() },
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                composable<Screen.Pairing> {
                    val readyState = state as? AppState.Ready
                    if (readyState != null) {
                        PairingScreen(
                            onBack = { navController.popBackStack() },
                            onPaired = {
                                rootViewModel.onPaired()
                                navController.popBackStack()
                            },
                        )
                    }
                }
                composable<Screen.Whiteboard> {
                    val readyState = state as? AppState.Ready
                    val partnershipId = readyState?.partnershipId
                    if (readyState != null && partnershipId != null) {
                        WhiteboardScreen(
                            userId = readyState.userId,
                            partnerId = readyState.partnerId.orEmpty(),
                            partnershipId = partnershipId,
                            onBack = { navController.popBackStack() },
                            onOpenArchive = { navController.navigate(Screen.Archive) },
                        )
                    }
                }
                composable<Screen.Archive> {
                    val readyState = state as? AppState.Ready
                    val partnershipId = readyState?.partnershipId
                    if (readyState != null && partnershipId != null) {
                        ArchiveScreen(
                            userId = readyState.userId,
                            partnerId = readyState.partnerId.orEmpty(),
                            partnershipId = partnershipId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
