package com.ste4n1.blechat.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ste4n1.blechat.R
import com.ste4n1.blechat.data.SettingsRepository
import com.ste4n1.blechat.ui.MainViewModel
import kotlinx.coroutines.launch
import java.io.File

private val presetColors = listOf(
    0xFF6750A4L,
    0xFF0061A4L,
    0xFF006E1CL,
    0xFF9A4500L,
    0xFF984061L,
    0xFF006A6AL
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val myName by vm.myName.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val useMaterialYou by vm.useMaterialYou.collectAsStateWithLifecycle()
    val customColor by vm.customColor.collectAsStateWithLifecycle()
    val backgroundPath by vm.chatBackground.collectAsStateWithLifecycle()
    val showStatus by vm.showStatus.collectAsStateWithLifecycle()
    val autoVisible by vm.autoVisible.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf(myName) }
    var nameSaved by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch { vm.setBackground(uri) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle(stringResource(R.string.profile)) }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameSaved = false
                    },
                    label = { Text(stringResource(R.string.your_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                vm.setName(name)
                                nameSaved = true
                            },
                            enabled = name.isNotBlank()
                        ) {
                            Text(if (nameSaved) stringResource(R.string.saved) else stringResource(R.string.save))
                        }
                    }
                )
            }

            item { SectionTitle(stringResource(R.string.appearance)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.theme), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip(stringResource(R.string.theme_system), SettingsRepository.THEME_SYSTEM, themeMode, vm::setThemeMode)
                        ThemeChip(stringResource(R.string.theme_light), SettingsRepository.THEME_LIGHT, themeMode, vm::setThemeMode)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip(stringResource(R.string.theme_dark_gray), SettingsRepository.THEME_DARK_GRAY, themeMode, vm::setThemeMode)
                        ThemeChip(stringResource(R.string.theme_amoled), SettingsRepository.THEME_AMOLED, themeMode, vm::setThemeMode)
                    }
                }
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.material_you),
                    subtitle = stringResource(R.string.material_you_subtitle),
                    checked = useMaterialYou,
                    onCheckedChange = vm::setUseMaterialYou
                )
            }
            if (!useMaterialYou) {
                item {
                    Column {
                        Text(stringResource(R.string.interface_color), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            presetColors.forEach { argb ->
                                val selected = customColor == argb
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(argb))
                                        .then(
                                            if (selected) {
                                                Modifier.border(
                                                    3.dp,
                                                    MaterialTheme.colorScheme.onSurface,
                                                    CircleShape
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable { vm.setCustomColor(argb) }
                                )
                            }
                        }
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.chat_background)) }
            item {
                Column {
                    if (backgroundPath != null) {
                        AsyncImage(
                            model = File(backgroundPath!!),
                            contentDescription = stringResource(R.string.chat_background_desc),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            backgroundLauncher.launch(arrayOf("image/*"))
                        }) {
                            Text(if (backgroundPath == null) stringResource(R.string.choose_wallpaper) else stringResource(R.string.change_wallpaper))
                        }
                        if (backgroundPath != null) {
                            OutlinedButton(onClick = { vm.clearBackground() }) {
                                Text(stringResource(R.string.remove))
                            }
                        }
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.visibility)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.auto_visibility),
                    subtitle = stringResource(R.string.auto_visibility_subtitle),
                    checked = autoVisible,
                    onCheckedChange = vm::setAutoVisible
                )
            }

            item { SectionTitle(stringResource(R.string.messages)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.send_statuses),
                    subtitle = stringResource(R.string.send_statuses_subtitle),
                    checked = showStatus,
                    onCheckedChange = vm::setShowStatus
                )
            }

            item { SectionTitle(stringResource(R.string.data)) }
            item {
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.clear_history))
                }
            }

            item { SectionTitle(stringResource(R.string.about)) }
            item {
                Column {
                    Text(stringResource(R.string.app_version), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.about_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_history_title)) },
            text = { Text(stringResource(R.string.clear_history_text)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearHistory()
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeChip(
    label: String,
    value: String,
    current: String,
    onSelect: (String) -> Unit
) {
    FilterChip(
        selected = current == value,
        onClick = { onSelect(value) },
        label = { Text(label) }
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
