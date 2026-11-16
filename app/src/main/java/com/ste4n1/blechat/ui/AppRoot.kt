package com.ste4n1.blechat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ste4n1.blechat.ui.screens.ChatScreen
import com.ste4n1.blechat.ui.screens.ConversationsScreen
import com.ste4n1.blechat.ui.screens.DiscoverScreen
import com.ste4n1.blechat.ui.screens.SettingsScreen

sealed interface Screen {
    data object Conversations : Screen
    data object Discover : Screen
    data object Settings : Screen
    data class Chat(val peerId: String, val peerName: String) : Screen
}

@Composable
fun AppRoot(vm: MainViewModel) {
    var screen by remember { mutableStateOf<Screen>(Screen.Conversations) }

    BackHandler(enabled = screen != Screen.Conversations) {
        screen = Screen.Conversations
    }

    when (val current = screen) {
        Screen.Conversations -> ConversationsScreen(
            vm = vm,
            onDiscover = { screen = Screen.Discover },
            onSettings = { screen = Screen.Settings },
            onOpenChat = { id, name -> screen = Screen.Chat(id, name) }
        )

        Screen.Discover -> DiscoverScreen(
            vm = vm,
            onBack = { screen = Screen.Conversations },
            onOpenChat = { id, name -> screen = Screen.Chat(id, name) }
        )

        Screen.Settings -> SettingsScreen(
            vm = vm,
            onBack = { screen = Screen.Conversations }
        )

        is Screen.Chat -> ChatScreen(
            vm = vm,
            peerId = current.peerId,
            peerName = current.peerName,
            onBack = { screen = Screen.Conversations }
        )
    }
}
