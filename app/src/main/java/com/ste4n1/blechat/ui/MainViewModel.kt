package com.ste4n1.blechat.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ste4n1.blechat.BleChatApp
import com.ste4n1.blechat.data.ConversationEntity
import com.ste4n1.blechat.data.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as BleChatApp).container
    private val manager = container.chatManager
    private val repository = container.repository
    private val settings = container.settings

    val devices = manager.devices
    val connectedPeers = manager.connectedPeers
    val discovering = manager.discovering
    val transferProgress = manager.transferProgress
    val status = manager.status
    val myName = manager.myName

    val themeMode = settings.themeMode
    val useMaterialYou = settings.useMaterialYou
    val customColor = settings.customColor
    val chatBackground = settings.chatBackground
    val showStatus = settings.showStatus
    val autoVisible = settings.autoVisible

    val conversations: StateFlow<List<ConversationEntity>> =
        repository.conversations().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    fun messages(conversationId: String): Flow<List<MessageEntity>> =
        repository.messages(conversationId)

    fun ensureStarted() = manager.ensureStarted()

    fun startDiscovery() = manager.startDiscovery()

    fun connect(address: String) = manager.connect(address)

    fun sendText(peerId: String, text: String) = manager.sendText(peerId, text)

    fun sendFile(peerId: String, uri: Uri) = manager.sendFile(peerId, uri)

    fun cancelSend(messageId: String) = manager.cancelSend(messageId)

    fun deleteMessage(messageId: String) = manager.deleteMessage(messageId)

    fun deleteMessageForEveryone(peerId: String, messageId: String) =
        manager.deleteMessageForEveryone(peerId, messageId)

    fun setName(name: String) = manager.setMyName(name)

    fun setThemeMode(mode: String) = settings.setThemeMode(mode)

    fun setUseMaterialYou(value: Boolean) = settings.setUseMaterialYou(value)

    fun setCustomColor(argb: Long) = settings.setCustomColor(argb)

    fun setShowStatus(value: Boolean) = settings.setShowStatus(value)

    fun setAutoVisible(value: Boolean) = settings.setAutoVisible(value)

    suspend fun setBackground(uri: Uri): Boolean = settings.setBackgroundFromUri(uri)

    fun clearBackground() = settings.clearBackground()

    fun clearHistory() {
        viewModelScope.launch { runCatching { repository.clearHistory() } }
    }

    fun bluetoothEnabled(): Boolean = manager.isBluetoothEnabled()

    fun discoverable(): Boolean = manager.isDiscoverable()
}
