package com.ste4n1.blechat

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ste4n1.blechat.ui.AppRoot
import com.ste4n1.blechat.ui.MainViewModel
import com.ste4n1.blechat.ui.theme.BleChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val themeMode by vm.themeMode.collectAsStateWithLifecycle()
            val useMaterialYou by vm.useMaterialYou.collectAsStateWithLifecycle()
            val customColor by vm.customColor.collectAsStateWithLifecycle()
            BleChatTheme(
                themeMode = themeMode,
                useMaterialYou = useMaterialYou,
                customColor = Color(customColor)
            ) {
                PermissionGate(vm) {
                    AppRoot(vm)
                }
            }
        }
    }
}

@Composable
private fun PermissionGate(vm: MainViewModel, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val autoVisible by vm.autoVisible.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(BtPermissions.hasAll(context)) }
    var askedDiscoverable by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = BtPermissions.hasAll(context)
    }
    val discoverableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        vm.ensureStarted()
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(BtPermissions.required())
    }

    LaunchedEffect(granted, autoVisible) {
        if (granted) {
            vm.ensureStarted()
            if (autoVisible && !askedDiscoverable && vm.bluetoothEnabled() && !vm.discoverable()) {
                askedDiscoverable = true
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
    }

    if (granted) {
        content()
    } else {
        PermissionScreen(onRequest = { launcher.launch(BtPermissions.required()) })
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.permission_rationale),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRequest) {
                Text(stringResource(R.string.allow_bluetooth))
            }
        }
    }
}
