package com.ste4n1.blechat.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ste4n1.blechat.R
import com.ste4n1.blechat.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenChat: (String, String) -> Unit
) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val discovering by vm.discovering.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var enabled by remember { mutableStateOf(vm.bluetoothEnabled()) }

    val discoverableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        vm.ensureStarted()
        vm.startDiscovery()
    }

    val requestDiscoverable = {
        if (enabled && !vm.discoverable()) {
            try {
                discoverableLauncher.launch(
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(
                        BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                        300
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    val enableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        enabled = vm.bluetoothEnabled()
        vm.ensureStarted()
        requestDiscoverable()
        vm.startDiscovery()
    }

    LaunchedEffect(Unit) {
        enabled = vm.bluetoothEnabled()
        vm.ensureStarted()
        requestDiscoverable()
        vm.startDiscovery()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nearby_devices)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.startDiscovery() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (discovering) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            if (!enabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.bluetooth_off), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.bluetooth_off_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                try {
                                    enableLauncher.launch(
                                        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                    )
                                } catch (_: Exception) {
                                }
                            }
                        ) {
                            Text(stringResource(R.string.turn_on_bluetooth))
                        }
                    }
                }
            }

            if (status.isNotBlank()) {
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (discovering) {
                            stringResource(R.string.searching_devices)
                        } else {
                            stringResource(R.string.no_devices_found)
                        },
                        modifier = Modifier.padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(devices, key = { it.address }) { device ->
                        ListItem(
                            headlineContent = { Text(device.name) },
                            supportingContent = {
                                Text(
                                    buildString {
                                        append(device.address)
                                        if (device.bonded) append(stringResource(R.string.paired_suffix))
                                        if (device.connected) append(stringResource(R.string.connected_suffix))
                                    }
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.Bluetooth,
                                    contentDescription = null,
                                    tint = if (device.connected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            },
                            trailingContent = {
                                TextButton(
                                    onClick = {
                                        if (!device.connected) vm.connect(device.address)
                                        onOpenChat(device.address, device.name)
                                    }
                                ) {
                                    Text(stringResource(if (device.connected) R.string.chat else R.string.connect))
                                }
                            },
                            modifier = Modifier.clickable {
                                if (!device.connected) vm.connect(device.address)
                                onOpenChat(device.address, device.name)
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
