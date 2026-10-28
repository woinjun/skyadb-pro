package com.sky22333.skyadb.ui.pointer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.pointer.BluetoothMouseSession
import com.sky22333.skyadb.pointer.MouseMotionBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BluetoothHost(val address: String, val name: String)
data class BluetoothUiState(
    val registered: Boolean = false,
    val hosts: List<BluetoothHost> = emptyList(),
    val selectedName: String = "",
)

class BluetoothPointerViewModel : PointerController() {
    private val bluetooth = MutableStateFlow(BluetoothUiState())
    val bluetoothState = bluetooth.asStateFlow()
    private var session: BluetoothMouseSession? = null
    private var worker: Job? = null
    private var timeout: Job? = null
    private var generation = 0L

    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        AppServices.context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override fun connect() {
        if (session != null) return
        if (Build.VERSION.SDK_INT < 28) { status(R.string.bluetooth_unsupported); return }
        if (!hasPermission()) { status(R.string.bluetooth_permission); return }
        try {
            val adapter = AppServices.context.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null) { status(R.string.bluetooth_unsupported); return }
            if (!adapter.isEnabled) { status(R.string.bluetooth_disabled); return }
            val token = ++generation
            state.value = state.value.copy(connecting = true, detail = "", status = R.string.bluetooth_registering)
            val active = BluetoothMouseSession(
                AppServices.context, adapter,
                onRegistered = {
                    if (generation == token) {
                        timeout?.cancel()
                        bluetooth.value = bluetooth.value.copy(registered = true)
                        refreshHosts()
                        state.value = state.value.copy(connecting = false, status = R.string.bluetooth_choose_host)
                    }
                },
                onConnection = { connected ->
                    if (generation == token) {
                        timeout?.cancel()
                        worker?.cancel()
                        motion = MouseMotionBuffer()
                        state.value = state.value.copy(
                            connecting = false, ready = connected, flying = false,
                            dragging = false, calibrated = false,
                            status = if (connected) R.string.pointer_ready else R.string.bluetooth_choose_host,
                        )
                        if (connected) startReports(token)
                        else bluetooth.value = bluetooth.value.copy(selectedName = "")
                    }
                },
                onFailure = { if (generation == token) inputFailed(it) },
            )
            session = active
            active.open()
            startTimeout(token)
        } catch (e: Exception) { inputFailed(e) }
    }

    @SuppressLint("MissingPermission")
    fun refreshHosts() {
        if (!hasPermission()) { disconnect(); status(R.string.bluetooth_permission); return }
        try {
            val adapter = AppServices.context.getSystemService(BluetoothManager::class.java)?.adapter
            val hosts = adapter?.bondedDevices.orEmpty().map {
                BluetoothHost(it.address, it.name?.takeIf(String::isNotBlank) ?: it.address)
            }.sortedBy { it.name }
            bluetooth.value = bluetooth.value.copy(hosts = hosts)
        } catch (e: Exception) { inputFailed(e) }
    }

    @SuppressLint("MissingPermission")
    fun selectHost(host: BluetoothHost) {
        if (Build.VERSION.SDK_INT < 28 || !bluetooth.value.registered || state.value.connecting || state.value.ready) return
        try {
            val adapter = AppServices.context.getSystemService(BluetoothManager::class.java)?.adapter
            val device = adapter?.bondedDevices?.firstOrNull { it.address == host.address }
            check(device?.bondState == BluetoothDevice.BOND_BONDED) { "Device is no longer paired" }
            state.value = state.value.copy(connecting = true, detail = "", status = R.string.pointer_connecting)
            bluetooth.value = bluetooth.value.copy(selectedName = host.name)
            session?.connect(checkNotNull(device))
            startTimeout(generation)
        } catch (e: Exception) { inputFailed(e) }
    }

    private fun startTimeout(token: Long) {
        timeout?.cancel()
        timeout = viewModelScope.launch {
            delay(15_000)
            if (generation == token && state.value.connecting) {
                inputFailed(IllegalStateException(AppServices.context.getString(R.string.bluetooth_timeout)))
            }
        }
    }

    private fun startReports(token: Long) {
        val active = session ?: return
        worker = viewModelScope.launch {
            try {
                while (isActive && generation == token) {
                    val reports = motion.drain()
                    if (Build.VERSION.SDK_INT >= 28) withContext(Dispatchers.IO) { reports.forEach(active::send) }
                    delay(16)
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { if (generation == token) inputFailed(e) }
        }
    }

    private fun status(message: Int) { state.value = state.value.copy(status = message, detail = "") }

    override fun inputFailed(error: Exception) {
        com.sky22333.skyadb.diagnostics.DiagnosticLogger.record(
            module = com.sky22333.skyadb.diagnostics.DiagnosticModule.App,
            operation = "Bluetooth HID", message = error.message.orEmpty(),
            suggestion = AppServices.context.getString(R.string.bluetooth_timeout), cause = error,
        )
        disconnect()
        state.value = state.value.copy(status = R.string.pointer_failed, detail = error.message.orEmpty().take(600))
    }

    override fun disconnect() {
        generation++
        worker?.cancel()
        timeout?.cancel()
        if (Build.VERSION.SDK_INT >= 28) session?.close()
        session = null
        motion = MouseMotionBuffer()
        bluetooth.value = BluetoothUiState()
        state.value = state.value.copy(
            ready = false, connecting = false, flying = false, dragging = false,
            calibrated = false, detail = "", status = R.string.pointer_disconnected,
        )
    }

    override fun onCleared() { disconnect() }
}
