package com.sky22333.skyadb.pointer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.annotation.RequiresApi

/** One foreground HID registration. Never reconnects a host without a user selection. */
@RequiresApi(28)
@SuppressLint("MissingPermission") // Caller checks CONNECT before opening; revocation is caught.
class BluetoothMouseSession(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val onRegistered: () -> Unit,
    private val onConnection: (Boolean) -> Unit,
    private val onFailure: (Exception) -> Unit,
) : AutoCloseable {
    @Volatile private var closed = false
    @Volatile private var proxy: BluetoothHidDevice? = null
    @Volatile private var host: BluetoothDevice? = null
    @Volatile private var selected: BluetoothDevice? = null
    @Volatile private var buttons: Byte = 0
    @Volatile private var bootProtocol = false

    private inline fun guarded(block: () -> Unit) {
        if (closed) return
        try { block() } catch (e: Exception) { onFailure(e) }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) = guarded {
            if (registered) onRegistered() else onFailure(IllegalStateException("HID registration ended"))
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) = guarded {
            if (device != selected) {
                if (state == BluetoothProfile.STATE_CONNECTED) proxy?.disconnect(device)
                return@guarded
            }
            if (state == BluetoothProfile.STATE_CONNECTED) {
                host = device
                buttons = 0
                bootProtocol = false
                onConnection(true)
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                host = null
                selected = null
                onConnection(false)
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) = guarded {
            if (device == host && type == BluetoothHidDevice.REPORT_TYPE_INPUT && id == 0.toByte()) {
                val report = byteArrayOf(buttons, 0, 0, 0).let { if (bootProtocol) it.copyOf(3) else it }
                if (bufferSize == 0 || bufferSize >= report.size) proxy?.replyReport(device, type, id, report)
                else proxy?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_PARAM)
            } else proxy?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
        }

        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) = guarded {
            if (device == host) bootProtocol = protocol == BluetoothHidDevice.PROTOCOL_BOOT_MODE
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) = guarded {
            proxy?.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) = guarded {
            if (device == selected) {
                host = null
                selected = null
                onConnection(false)
            }
        }
    }

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, service: BluetoothProfile) {
            if (closed) { runCatching { adapter.closeProfileProxy(profile, service) }; return }
            guarded {
                val hid = service as BluetoothHidDevice
                proxy = hid
                check(hid.registerApp(
                    BluetoothHidDeviceAppSdpSettings(
                        "SkyADB Pro Mouse", "Touchpad and air mouse", "SkyADB Pro",
                        BluetoothHidDevice.SUBCLASS1_MOUSE, MouseProtocol.descriptor,
                    ), null, null, context.mainExecutor, callback,
                )) { "HID registration request rejected" }
            }
        }
        override fun onServiceDisconnected(profile: Int) = guarded {
            proxy = null
            host = null
            onFailure(IllegalStateException("Bluetooth HID service disconnected"))
        }
    }

    fun open() {
        check(adapter.getProfileProxy(context, listener, BluetoothProfile.HID_DEVICE)) {
            "Bluetooth HID device profile unavailable"
        }
    }

    fun connect(device: BluetoothDevice) {
        check(!closed && host == null && selected == null)
        selected = device
        check(proxy?.connect(device) == true) { "Bluetooth connection request rejected" }
    }

    @Synchronized fun send(frame: ByteArray) {
        check(!closed)
        val device = checkNotNull(host)
        val report = MouseProtocol.hidReport(frame, bootProtocol)
        check(proxy?.sendReport(device, 0, report) == true) { "Bluetooth mouse report rejected" }
        buttons = report[0]
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        val hid = proxy
        proxy = null
        val device = host ?: selected
        host = null
        selected = null
        if (hid != null) {
            if (device != null) {
                runCatching { hid.sendReport(device, 0, ByteArray(if (bootProtocol) 3 else 4)) }
                runCatching { hid.disconnect(device) }
            }
            runCatching { hid.unregisterApp() }
            runCatching { adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid) }
        }
    }
}
