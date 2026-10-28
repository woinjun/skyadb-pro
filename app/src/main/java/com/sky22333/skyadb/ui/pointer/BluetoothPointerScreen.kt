package com.sky22333.skyadb.ui.pointer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BluetoothPointerScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: BluetoothPointerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bluetooth by viewModel.bluetoothState.collectAsStateWithLifecycle()
    var launchError by remember { mutableStateOf(false) }
    val systemUi = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.connect()
        if (viewModel.hasPermission()) viewModel.refreshHosts()
    }
    val discoverable: () -> Unit = {
        try {
            systemUi.launch(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120))
        } catch (_: Exception) { launchError = true }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.connect()
    }
    val advertising = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) discoverable() else launchError = true
    }
    PointerScreen(bottomPadding, onBackClick, viewModel, bluetooth = true) {
        if (Build.VERSION.SDK_INT >= 28) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.ready && !state.connecting) {
                    Button(onClick = {
                        launchError = false
                        if (Build.VERSION.SDK_INT >= 31 && !viewModel.hasPermission()) {
                            permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                        } else {
                            viewModel.connect()
                            viewModel.refreshHosts()
                        }
                    }) { Text(stringResource(R.string.bluetooth_prepare)) }
                }
                OutlinedButton(onClick = {
                    try { systemUi.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                    catch (_: Exception) { launchError = true }
                }) { Text(stringResource(R.string.bluetooth_settings)) }
                if (bluetooth.registered && !state.ready && !state.connecting) {
                    OutlinedButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                            advertising.launch(Manifest.permission.BLUETOOTH_ADVERTISE)
                        } else discoverable()
                    }) { Text(stringResource(R.string.bluetooth_pair)) }
                }
            }
            if (launchError) Text(stringResource(R.string.bluetooth_system_error), color = MaterialTheme.colorScheme.error)
            if (bluetooth.selectedName.isNotEmpty()) Text(stringResource(R.string.bluetooth_target, bluetooth.selectedName))
            if (bluetooth.registered && !state.ready) {
                Text(stringResource(R.string.bluetooth_pair_hint), style = MaterialTheme.typography.bodySmall)
                if (bluetooth.hosts.isEmpty()) Text(stringResource(R.string.bluetooth_empty))
                bluetooth.hosts.forEach { host ->
                    OutlinedButton(
                        onClick = { viewModel.selectHost(host) }, enabled = !state.connecting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("${host.name}\n${host.address}") }
                }
            }
            if (state.ready) OutlinedButton(onClick = { viewModel.disconnect(); viewModel.connect() }) {
                Text(stringResource(R.string.bluetooth_change_host))
            }
        }
    }
}
