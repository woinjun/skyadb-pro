package com.sky22333.skyadb.ui.pointer

import androidx.lifecycle.viewModelScope
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.pointer.AdbMouseSession
import com.sky22333.skyadb.pointer.MouseMotionBuffer
import java.io.EOFException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PointerUiState(
    val connecting: Boolean = false,
    val ready: Boolean = false,
    val status: Int = R.string.pointer_disconnected,
    val detail: String = "",
    val flying: Boolean = false,
    val calibrated: Boolean = false,
    val dragging: Boolean = false,
    val sensitivity: Float = 1f,
)

class PointerViewModel : PointerController() {
    private val generation = AtomicLong()
    private val session = AtomicReference<AdbMouseSession?>()
    private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null
    private var logs: Job? = null
    private var reader: Job? = null

    override fun connect() {
        if (state.value.connecting || state.value.ready) return
        val token = generation.incrementAndGet()
        motion = MouseMotionBuffer()
        state.value = state.value.copy(connecting = true, detail = "", status = R.string.pointer_connecting)
        worker = viewModelScope.launch(Dispatchers.IO) {
            var opened: AdbMouseSession? = null
            try {
                val manager = AppServices.kadbManager
                val endpoint = manager.currentEndpoint()
                val client = when (val result = manager.mirrorClient()) {
                    is AdbOperationResult.Success -> result.data
                    is AdbOperationResult.Failure -> error(result.message)
                }
                val permission = client.shell("test -r /dev/uhid && test -w /dev/uhid")
                if (permission.exitCode != 0) error(AppServices.context.getString(R.string.pointer_unsupported))
                opened = AdbMouseSession.open(AppServices.context, client)
                ensureActive()
                if (generation.get() != token) return@launch
                session.set(opened)
                val active = opened
                logs = viewModelScope.launch(Dispatchers.IO) {
                    try {
                        while (isActive) {
                            val line = active.readLog() ?: throw EOFException("scrcpy server stopped")
                            if (line.contains("ERROR:")) error(line)
                        }
                    } catch (e: Exception) {
                        if (e !is CancellationException) fail(token, e)
                    }
                }
                reader = viewModelScope.launch(Dispatchers.IO) {
                    try {
                        while (isActive) active.readControl()
                    } catch (e: Exception) {
                        if (e !is CancellationException) fail(token, e)
                    }
                }
                synchronized(this@PointerViewModel) {
                    if (generation.get() != token) return@launch
                    state.value = state.value.copy(connecting = false, ready = true, status = R.string.pointer_ready)
                }
                while (isActive) {
                    check(manager.currentEndpoint() == endpoint) { AppServices.context.getString(R.string.pointer_disconnected) }
                    motion.drain().forEach(active::send)
                    delay(16)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) fail(token, e)
            } finally {
                opened?.close()
                session.compareAndSet(opened, null)
            }
        }
    }

    @Synchronized private fun fail(token: Long, error: Exception) {
        if (generation.get() != token) return
        disconnect()
        state.value = state.value.copy(status = R.string.pointer_failed, detail = error.message.orEmpty().take(600))
    }

    @Synchronized override fun disconnect() {
        generation.incrementAndGet()
        val active = session.getAndSet(null)
        cleanup.launch { active?.close() }
        worker?.cancel()
        logs?.cancel()
        reader?.cancel()
        state.value = state.value.copy(
            connecting = false, ready = false, flying = false, dragging = false,
            calibrated = false, status = R.string.pointer_disconnected,
        )
    }

    override fun inputFailed(error: Exception) { fail(generation.get(), error) }

    override fun onCleared() {
        disconnect()
    }
}
