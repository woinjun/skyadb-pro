package com.sky22333.skyadb.ui.pointer

import androidx.lifecycle.ViewModel
import com.sky22333.skyadb.pointer.GyroMotion
import com.sky22333.skyadb.pointer.MouseMotionBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared gestures and gyro processing; connections belong to each transport. */
abstract class PointerController : ViewModel() {
    protected val state = MutableStateFlow(PointerUiState())
    val uiState = state.asStateFlow()
    protected var motion = MouseMotionBuffer()
    private var gyro = GyroMotion()
    abstract fun connect()
    abstract fun disconnect()
    protected abstract fun inputFailed(error: Exception)
    fun move(x: Float, y: Float) {
        if (state.value.ready) motion.move(x * state.value.sensitivity, y * state.value.sensitivity)
    }

    fun scroll(amount: Float) {
        if (state.value.ready) motion.move(0f, 0f, amount)
    }

    fun click(right: Boolean = false) {
        if (!state.value.ready) return
        try {
            // Release a drag before sending a complete click.
            if (state.value.dragging) motion.button(0)
            motion.button(if (right) 2 else 1)
            motion.button(0)
            state.value = state.value.copy(dragging = false)
        } catch (e: IllegalStateException) { inputFailed(e) }
    }

    fun toggleDrag() {
        if (!state.value.ready) return
        try {
            val next = !state.value.dragging
            motion.button(if (next) 1 else 0)
            state.value = state.value.copy(dragging = next)
        } catch (e: IllegalStateException) { inputFailed(e) }
    }

    fun sensitivity(value: Float) { state.value = state.value.copy(sensitivity = value.coerceIn(0.3f, 3f)) }

    fun flying(enabled: Boolean) {
        gyro = GyroMotion()
        state.value = state.value.copy(flying = enabled && state.value.ready, calibrated = false)
    }

    fun recalibrate() { gyro = GyroMotion(); state.value = state.value.copy(calibrated = false) }

    fun sensor(timestamp: Long, values: FloatArray, rotation: Int) {
        if (!state.value.ready || !state.value.flying) return
        val (x, y) = gyro.sample(timestamp, values, rotation, state.value.sensitivity)
        motion.move(x, y)
        if (state.value.calibrated != gyro.calibrated) state.value = state.value.copy(calibrated = gyro.calibrated)
    }

}
