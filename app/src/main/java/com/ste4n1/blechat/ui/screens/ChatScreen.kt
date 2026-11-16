package com.ste4n1.blechat.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ste4n1.blechat.R
import com.ste4n1.blechat.data.MessageEntity
import com.ste4n1.blechat.ui.MainViewModel
import com.ste4n1.blechat.ui.formatSize
import com.ste4n1.blechat.ui.formatTime
import com.ste4n1.blechat.ui.openFile
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    vm: MainViewModel,
    peerId: String,
    peerName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val messages by remember(peerId) { vm.messages(peerId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val connectedPeers by vm.connectedPeers.collectAsStateWithLifecycle()
    val progress by vm.transferProgress.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val backgroundPath by vm.chatBackground.collectAsStateWithLifecycle()
    val showStatus by vm.showStatus.collectAsStateWithLifecycle()
    val isConnected = peerId in connectedPeers

    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.sendFile(peerId, uri)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (backgroundPath != null) {
            AsyncImage(
                model = File(backgroundPath!!),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f))
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ),
                    title = {
                        Column {
                            Text(peerName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = if (isConnected) stringResource(R.string.connected) else stringResource(R.string.not_connected),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isConnected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Column {
                        if (!isConnected) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = status.ifBlank { stringResource(R.string.no_connection_device) },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { vm.connect(peerId) }) {
                                    Text(stringResource(R.string.connect))
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            IconButton(
                                onClick = { fileLauncher.launch(arrayOf("*/*")) },
                                enabled = isConnected
                            ) {
                                Icon(Icons.Filled.AttachFile, contentDescription = stringResource(R.string.attachment))
                            }
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(stringResource(R.string.message_hint)) },
                                maxLines = 5,
                                shape = RoundedCornerShape(24.dp),
                                enabled = isConnected
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    val text = input.trim()
                                    if (text.isNotEmpty()) {
                                        vm.sendText(peerId, text)
                                        input = ""
                                    }
                                },
                                enabled = isConnected && input.isNotBlank()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
                            }
                        }
                    }
                }
            }
        ) { padding ->
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isConnected) {
                            stringResource(R.string.write_first_message)
                        } else {
                            stringResource(R.string.connect_to_start_chat)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        Box(modifier = Modifier.animateItem()) {
                            MessageBubble(
                                message = message,
                                progress = progress[message.id],
                                showStatus = showStatus,
                                onOpenFile = {
                                    if (message.type == "FILE") {
                                        openFile(context, message.filePath, message.mimeType)
                                    }
                                },
                                onCancel = { vm.cancelSend(message.id) },
                                onDelete = { vm.deleteMessage(message.id) },
                                onDeleteForEveryone = { vm.deleteMessageForEveryone(peerId, message.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageEntity,
    progress: Float?,
    showStatus: Boolean,
    onOpenFile: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onDeleteForEveryone: () -> Unit
) {
    val mine = message.isMine
    var menuOpen by remember { mutableStateOf(false) }
    val canCancel = mine && message.status == "SENDING" && message.type == "FILE"
    val canDeleteForEveryone = mine && message.status != "SENDING"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(180)),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (mine) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                },
                tonalElevation = if (mine) 1.dp else 0.dp,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .combinedClickable(
                        onClick = {
                            if (message.type == "FILE" && message.status != "SENDING") onOpenFile()
                        },
                        onLongClick = { menuOpen = true }
                    )
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (message.type == "FILE") {
                        val isImage = message.mimeType?.startsWith("image/") == true
                        if (isImage && message.filePath != null && message.status != "FAILED") {
                            AsyncImage(
                                model = File(message.filePath),
                                contentDescription = message.text,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(210.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FileTypeBadge(
                                name = message.text ?: stringResource(R.string.file),
                                mime = message.mimeType
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = message.text ?: stringResource(R.string.file),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatSize(message.fileSize),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (canCancel) {
                                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.cancel_send),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = progress != null,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            if (progress != null) {
                                Column {
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = message.text ?: "",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (mine && showStatus) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = statusLabel(message.status),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (message.status == "FAILED" || message.status == "CANCELLED") {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                if (canCancel) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cancel_sending)) },
                        onClick = {
                            menuOpen = false
                            onCancel()
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_for_me)) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                    }
                )
                if (canDeleteForEveryone) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_for_everyone)) },
                        onClick = {
                            menuOpen = false
                            onDeleteForEveryone()
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileTypeBadge(name: String, mime: String?) {
    val (icon, tint) = fileIcon(name, mime)
    Surface(
        shape = CircleShape,
        color = tint.copy(alpha = 0.16f),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun fileIcon(name: String, mime: String?): Pair<ImageVector, Color> {
    val lower = name.lowercase()
    val m = (mime ?: "").lowercase()
    return when {
        lower.endsWith(".apk") || m.contains("android.package-archive") ->
            Icons.Filled.Android to Color(0xFF3DDC84)

        lower.endsWith(".pdf") || m == "application/pdf" ->
            Icons.Filled.PictureAsPdf to MaterialTheme.colorScheme.error

        m.startsWith("image/") || lower.endsWith(".png") || lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".gif") ->
            Icons.Filled.Image to MaterialTheme.colorScheme.tertiary

        m.startsWith("video/") || lower.endsWith(".mp4") || lower.endsWith(".mkv") ||
            lower.endsWith(".mov") || lower.endsWith(".webm") ->
            Icons.Filled.VideoFile to MaterialTheme.colorScheme.secondary

        m.startsWith("audio/") || lower.endsWith(".mp3") || lower.endsWith(".wav") ||
            lower.endsWith(".ogg") || lower.endsWith(".m4a") || lower.endsWith(".flac") ->
            Icons.Filled.AudioFile to MaterialTheme.colorScheme.primary

        lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") ||
            lower.endsWith(".tar") || lower.endsWith(".gz") ->
            Icons.Filled.Archive to MaterialTheme.colorScheme.tertiary

        lower.endsWith(".txt") || lower.endsWith(".doc") || lower.endsWith(".docx") ||
            lower.endsWith(".rtf") || lower.endsWith(".md") || m.startsWith("text/") ->
            Icons.Filled.Description to MaterialTheme.colorScheme.primary

        else -> Icons.AutoMirrored.Filled.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun statusLabel(status: String): String = when (status) {
    "SENDING" -> stringResource(R.string.status_sending)
    "SENT" -> stringResource(R.string.status_sent)
    "FAILED" -> stringResource(R.string.status_failed)
    "CANCELLED" -> stringResource(R.string.status_cancelled)
    else -> ""
}
