package com.hoshina.assistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.hoshina.assistant.ui.chat.ChatScreen
import com.hoshina.assistant.ui.chat.ChatViewModel
import com.hoshina.assistant.ui.call.CallScreen
import com.hoshina.assistant.ui.settings.SettingsScreen
import com.hoshina.assistant.ui.settings.SettingsViewModel
import androidx.compose.ui.Modifier

object Routes {
    const val CHAT = "chat"
    const val CALL = "call"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = Routes.CHAT,
        modifier = modifier,
    ) {
        composable(Routes.CHAT) {
            ChatScreen(
                viewModel = chatViewModel,
                aiName = settingsState.aiName,
                showDeviceTime = settingsState.isDeviceTimeVisible,
                onOpenCall = { navController.navigate(Routes.CALL) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.CALL) {
            CallScreen(
                viewModel = chatViewModel,
                aiName = settingsState.aiName,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
